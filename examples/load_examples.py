"""Load the Datorio examples into a running quick start stack.

Creates a ClickHouse data source for the sample data, the import source of the Excel example and the
example reports from examples/reports. Run it after `docker compose up -d`:

    python3 examples/load_examples.py

ADMIN_USER, ADMIN_PASSWORD, APP_PORT, AIRFLOW_PORT and CH_PASSWORD are read from .env when it exists.
Objects that already exist (by name) are left as they are, so the script can be run again. In a GitHub
Codespace the report links point to the forwarded address of the port. Needs Python 3.8+ only.
"""
import argparse
import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
EXAMPLES = ROOT / 'examples'
DATASOURCE_NAME = 'Sample ClickHouse'


def read_env():
    env = {}
    path = ROOT / '.env'
    if path.exists():
        for line in path.read_text(encoding='utf-8').splitlines():
            line = line.strip()
            if line and not line.startswith('#') and '=' in line:
                key, value = line.split('=', 1)
                env[key.strip()] = value.strip()
    return env


class Api:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip('/')
        self.token = None

    def call(self, method, path, body=None):
        request = urllib.request.Request(self.base_url + '/api' + path, method=method,
                                         data=None if body is None else json.dumps(body).encode())
        request.add_header('Content-Type', 'application/json')
        if self.token:
            request.add_header('Authorization', 'Bearer ' + self.token)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                text = response.read().decode()
        except urllib.error.HTTPError as e:
            raise SystemExit(f'{method} {path} failed: {e.code} {e.read().decode()[:500]}')
        return json.loads(text) if text else None

    def wait_until_ready(self, seconds):
        deadline = time.time() + seconds
        while True:
            try:
                with urllib.request.urlopen(self.base_url + '/api/health', timeout=5):
                    return
            except (urllib.error.URLError, OSError):
                if time.time() > deadline:
                    raise SystemExit(f'Datorio does not answer at {self.base_url}; is the stack running?')
                time.sleep(3)


def main():
    env = read_env()
    parser = argparse.ArgumentParser(description='Load the Datorio examples')
    app_port = env.get('APP_PORT', '8080')
    parser.add_argument('--url', default=f'http://localhost:{app_port}')
    parser.add_argument('--user', default=env.get('ADMIN_USER', 'admin'))
    parser.add_argument('--password', default=env.get('ADMIN_PASSWORD', 'admin'))
    parser.add_argument('--clickhouse-password', default=env.get('CH_PASSWORD', 'clickhouse'))
    args = parser.parse_args()

    api = Api(args.url)
    api.wait_until_ready(180)
    api.token = api.call('POST', '/auth/login', {'username': args.user, 'password': args.password})['accessToken']

    # The data source as the Datorio container sees ClickHouse: by the service name of docker-compose.yml
    datasource = next((d for d in api.call('GET', '/datasources') if d['name'] == DATASOURCE_NAME), None)
    if datasource:
        print(f'Data source "{DATASOURCE_NAME}" exists (id {datasource["id"]})')
    else:
        datasource = api.call('POST', '/datasources', {
            'name': DATASOURCE_NAME, 'description': 'ClickHouse of the quick start with the sample tables',
            'type': 'CLICKHOUSE', 'host': 'clickhouse', 'port': 8123, 'databaseName': 'sample',
            'username': 'default', 'password': args.clickhouse_password})
        print(f'Data source "{DATASOURCE_NAME}" created (id {datasource["id"]})')
    test = api.call('POST', f'/datasources/{datasource["id"]}/test')
    if not test.get('success'):
        raise SystemExit(f'The data source does not connect: {test.get("message")}')

    source = json.loads((EXAMPLES / 'sales-import-source.json').read_text(encoding='utf-8'))
    existing = {s['name'] for s in api.call('GET', '/import/sources')}
    if source['name'] in existing:
        print(f'Import source "{source["name"]}" exists')
    else:
        api.call('POST', '/import/sources', {**source, 'datasourceId': datasource['id']})
        print(f'Import source "{source["name"]}" created: upload examples/sales_2026_01.xlsx with it on the '
              f'Upload tab of Data Import')

    # The browser of a Codespace reaches a port through its forwarded address, not localhost
    codespace = os.environ.get('CODESPACE_NAME')
    domain = os.environ.get('GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN')
    link_base = f'https://{codespace}-{app_port}.{domain}' if codespace and domain else args.url

    reports = {r['name']: r['id'] for r in api.call('GET', '/reports?size=1000')['content']}
    for path in sorted((EXAMPLES / 'reports').glob('*.json')):
        config = json.loads(path.read_text(encoding='utf-8'))
        if config['name'] in reports:
            report_id = reports[config['name']]
            print(f'Report "{config["name"]}" exists')
        else:
            report_id = api.call('POST', '/templates/import',
                                 {'config': config, 'datasourceId': datasource['id']})['reportId']
            print(f'Report "{config["name"]}" imported')
        print(f'  {link_base}/reports/{report_id}')

    airflow = f"http://localhost:{env.get('AIRFLOW_PORT', '8088')}/api/v2/version"
    try:
        urllib.request.urlopen(airflow, timeout=5).close()
    except (urllib.error.URLError, OSError):
        print('The report "Airflow job example" needs the Airflow container: '
              'docker compose -f docker-compose.yml -f examples/airflow/compose.yml up -d')


if __name__ == '__main__':
    sys.exit(main())
