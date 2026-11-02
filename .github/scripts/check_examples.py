"""CI check of the examples against the containers of docker-compose.yml and examples/airflow/compose.yml.

1. The sample data of docker/clickhouse/init.sql is loaded and looks as expected.
2. The example DAG, started the way the report button starts it, succeeds, and fails at the step the
   report asks for; both runs are recorded in sample.job_runs.
3. Every query of the example reports runs with the default parameter values and returns rows, and the
   widget a drill-replace shows returns rows for the first value of the chart it replaces.

With the argument "datorio" it checks the application after examples/load_examples.py instead: the
report list loads, the three example reports render without widget errors, and the button of the
Airflow job example starts the DAG through Datorio and the run succeeds.
"""
import base64
import datetime
import json
import pathlib
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
CLICKHOUSE = 'http://localhost:8123'
CLICKHOUSE_AUTH = 'Basic ' + base64.b64encode(b'default:clickhouse').decode()
AIRFLOW = 'http://localhost:8088'
DATORIO = 'http://localhost:8080/api'
DAG_ID = 'datorio_demo_sales_summary'


def http(method, url, body=None, headers=None):
    data = body if isinstance(body, (bytes, type(None))) else json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method, headers={'Content-Type': 'application/json',
                                                                              **(headers or {})})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read().decode()


def rows(sql):
    text = http('POST', CLICKHOUSE, (sql + '\nFORMAT JSON').encode(), {'Authorization': CLICKHOUSE_AUTH})
    return json.loads(text)['data']


def wait(what, check, seconds):
    deadline = time.time() + seconds
    while True:
        try:
            result = check()
            if result:
                return result
        except (urllib.error.URLError, OSError, KeyError, ValueError):
            pass
        if time.time() > deadline:
            sys.exit(f'Timed out waiting for {what}')
        time.sleep(3)


def check_sample_data():
    # The page views are inserted last by init.sql; the count must stop growing, since the parts of a running
    # INSERT ... SELECT are visible before it ends
    counts = []

    def settled():
        counts.append(int(rows('SELECT count() AS n FROM sample.page_views')[0]['n']))
        return len(counts) >= 2 and counts[-1] > 0 and counts[-1] == counts[-2]
    wait('the sample data', settled, 240)
    sales = rows("""SELECT count() AS n, uniqExact(order_id) AS ids, toString(min(sale_date)) AS first,
    toString(max(sale_date)) AS last, round(sum(total_amount)) AS revenue FROM sample.sales""")[0]
    views = rows("""SELECT count() AS n, uniqExact(session_id) AS sessions, toString(min(event_date)) AS first,
    toString(max(event_date)) AS last FROM sample.page_views""")[0]
    print('sample.sales', sales)
    print('sample.page_views', views)
    assert int(sales['n']) >= 30000 and sales['ids'] == sales['n'], sales
    assert (sales['first'], sales['last']) == ('2024-01-01', '2025-12-31'), sales
    assert int(views['n']) >= 200000 and (views['first'], views['last']) == ('2024-01-01', '2025-12-31'), views


def check_dag():
    token = json.loads(http('POST', AIRFLOW + '/auth/token', {'username': 'datorio', 'password': 'datorio'}))
    auth = {'Authorization': 'Bearer ' + token['access_token']}
    dag_url = f'{AIRFLOW}/api/v2/dags/{DAG_ID}'

    def dag_loaded():
        errors = json.loads(http('GET', AIRFLOW + '/api/v2/importErrors', headers=auth))['import_errors']
        if errors:
            sys.exit('DAG import errors: ' + json.dumps(errors, indent=2))
        return json.loads(http('GET', dag_url, headers=auth))
    wait('the example DAG in Airflow', dag_loaded, 300)

    def run(conf, expected_state):
        run_id = json.loads(http('POST', dag_url + '/dagRuns', {'logical_date': None, 'conf': conf},
                                 auth))['dag_run_id']
        state = wait(f'the run {run_id}', lambda: (lambda s: s if s in ('success', 'failed') else None)(
            json.loads(http('GET', f'{dag_url}/dagRuns/{run_id}', headers=auth))['state']), 300)
        tasks = json.loads(http('GET', f'{dag_url}/dagRuns/{run_id}/taskInstances', headers=auth))['task_instances']
        print(run_id, state, [(t['task_display_name'], t['state']) for t in tasks])
        assert state == expected_state, (run_id, state)
        return run_id

    conf = {'region': 'North', 'date_from': '2025-01-01', 'date_to': '2025-12-31', 'started_by': 'ci'}
    ok = run({**conf, 'fail_at_step': 0}, 'success')
    failed = run({**conf, 'fail_at_step': 2}, 'failed')
    recorded = {r['run_id']: r for r in rows('SELECT run_id, status, failed_step, orders, revenue FROM sample.job_runs')}
    print('sample.job_runs', recorded)
    assert recorded[ok]['status'] == 'success' and int(recorded[ok]['orders']) > 0, recorded
    assert recorded[failed]['status'] == 'failed' and recorded[failed]['failed_step'] == 'Summarize by category'
    categories = rows(f"SELECT count() AS n FROM sample.job_results WHERE run_id = '{ok}'")[0]['n']
    assert int(categories) == 4, categories


def resolve(sql, values):
    """Put the parameter values into the query the way Datorio does: quoted literals, '' when empty."""
    def literal(match):
        value = values.get(match.group(1))
        if value in (None, ''):
            return "''"
        return "'" + str(value).replace('\\', '\\\\').replace("'", "\\'") + "'"
    return re.sub(r':([a-zA-Z_][a-zA-Z0-9_]*)', literal, sql)


def check_reports():
    today = datetime.date.today().isoformat()
    for path in sorted((ROOT / 'examples' / 'reports').glob('*.json')):
        report = json.loads(path.read_text(encoding='utf-8'))
        values = {p['name']: today if p['defaultValue'] == '__today__' else p['defaultValue']
                  for p in report['parameters']}
        queries = [(f"options of {c['parameterName']}", c['optionsQuery'])
                   for c in report['parameterControls'] if c['optionsQuery']]
        queries += [(w['title'] or w['widgetType'], w['rawSql']) for w in report['widgets'] if w['rawSql']]
        for title, sql in queries:
            try:
                result = rows(resolve(sql, values))
            except urllib.error.HTTPError as e:
                sys.exit(f'{report["name"]} / {title}: {e.read().decode()[:1000]}')
            print(f'{report["name"]} / {title}: {len(result)} rows')
            assert result, f'{report["name"]} / {title} returned no rows'
        # A click on the first value of a drill-replace source puts it into the parameter of the hidden widget
        widgets = {w['sortOrder']: w for w in report['widgets']}
        for action in report.get('actions', []):
            source = widgets[action['sourceWidgetSortOrder']]
            value = rows(resolve(source['rawSql'], values))[0][action['sourceField']]
            param = json.loads(action['config'])['paramName']
            for target in action['targetWidgetSortOrders'].split(','):
                widget = widgets[int(target)]
                result = rows(resolve(widget['rawSql'], {**values, param: value}))
                print(f'{report["name"]} / {widget["title"]} for {param} = {value}: {len(result)} rows')
                assert result, f'{report["name"]} / {widget["title"]} returned no rows for {param} = {value}'


def check_datorio():
    token = json.loads(http('POST', DATORIO + '/auth/login', {'username': 'admin', 'password': 'admin'}))['accessToken']
    auth = {'Authorization': 'Bearer ' + token}
    reports = {r['name']: r['id'] for r in json.loads(http('GET', DATORIO + '/reports?size=100', headers=auth))['content']}
    print('reports', reports)
    expected = {json.loads(p.read_text(encoding='utf-8'))['name'] for p in (ROOT / 'examples' / 'reports').glob('*.json')}
    assert expected <= set(reports), expected - set(reports)
    for name in sorted(expected):
        result = json.loads(http('POST', f'{DATORIO}/reports/{reports[name]}/render', {}, auth))
        for w in result['widgets']:
            title = w.get('title') or w['widgetType']
            assert not w.get('error'), f'{name} / {title}: {w["error"]}'
            if w['widgetType'] in ('CHART', 'TABLE', 'KPI'):
                assert w.get('data') and w['data']['rowCount'] > 0, f'{name} / {title} has no rows'
        print(f'{name}: {len(result["widgets"])} widgets rendered')

    # The button of the Airflow job example, the way the report page presses it
    report_id = reports['Airflow job example']
    widgets = json.loads(http('GET', f'{DATORIO}/reports/{report_id}/widgets', headers=auth))
    button = next(w for w in widgets if w['widgetType'] == 'BUTTON')
    params = {'region': 'South', 'date_from': '2025-01-01', 'date_to': '2025-06-30', 'fail_at_step': '0'}
    run = json.loads(http('POST', f'{DATORIO}/reports/{report_id}/widgets/{button["id"]}/run-job',
                          {'parameters': params}, auth))
    status_url = (f'{DATORIO}/reports/{report_id}/widgets/{button["id"]}/run-job/status?runId='
                  + urllib.parse.quote(run['runId'], safe=''))
    status = wait('the run started by the button',
                  lambda: (lambda s: s if s['finished'] else None)(json.loads(http('GET', status_url, headers=auth))),
                  300)
    print('button run', run['runId'], status['state'], status['doneTasks'], '/', status['totalTasks'])
    assert status['state'] == 'success', status


if sys.argv[1:] == ['datorio']:
    check_datorio()
else:
    check_sample_data()
    check_dag()
    check_reports()
print('Examples OK')
