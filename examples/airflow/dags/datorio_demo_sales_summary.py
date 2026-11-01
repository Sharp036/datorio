"""Example DAG started by the "Start the job" button of the Datorio report "Airflow job example".

Datorio sends the report parameters as the run configuration: region ("All" or a region), date_from and
date_to (YYYY-MM-DD), fail_at_step (0 = no error, 1-4 = fail at that step) and started_by (the user who
pressed the button). The DAG summarizes sample.sales for that region and period by category, compares the
revenue with the previous period of the same length and writes the result to sample.job_results and
sample.job_runs, which the report shows when the run is over. Each step waits a few seconds, so the
progress bar under the button shows the steps one by one.
"""
import json
import os
import time
import urllib.parse
import urllib.request
from datetime import date, timedelta

import pendulum
from airflow.sdk import dag, get_current_context, task

CLICKHOUSE_URL = os.environ.get('CLICKHOUSE_URL', 'http://clickhouse:8123')
CLICKHOUSE_USER = os.environ.get('CLICKHOUSE_USER', 'default')
CLICKHOUSE_PASSWORD = os.environ.get('CLICKHOUSE_PASSWORD', '')
STEP_SECONDS = int(os.environ.get('DATORIO_DEMO_STEP_SECONDS', '8'))

# The same tables as docker/clickhouse/init.sql, for a ClickHouse volume created before they were added
TABLES = [
    """CREATE TABLE IF NOT EXISTS sample.job_runs (
    run_id String, started_by String, region String, date_from Date, date_to Date,
    status LowCardinality(String), failed_step String, message String,
    orders UInt64, revenue Float64, previous_revenue Float64, finished_at DateTime
) ENGINE = MergeTree() ORDER BY finished_at""",
    """CREATE TABLE IF NOT EXISTS sample.job_results (
    run_id String, category String, orders UInt64, revenue Float64, top_product String
) ENGINE = MergeTree() ORDER BY (run_id, category)""",
]

FILTER = ("sale_date BETWEEN {date_from:Date} AND {date_to:Date} "
          "AND ({region:String} = 'All' OR region = {region:String})")


def clickhouse(sql, params=None, rows=None):
    """Run a query over the ClickHouse HTTP interface; rows are inserted as JSONEachRow."""
    query = {f'param_{k}': v for k, v in (params or {}).items()}
    if rows is None:
        body = sql.encode()
    else:
        query['query'] = sql + ' FORMAT JSONEachRow'
        body = '\n'.join(json.dumps(r) for r in rows).encode()
    request = urllib.request.Request(
        CLICKHOUSE_URL + '/?' + urllib.parse.urlencode(query), data=body,
        headers={'X-ClickHouse-User': CLICKHOUSE_USER, 'X-ClickHouse-Key': CLICKHOUSE_PASSWORD})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read().decode()


def select(sql, params):
    text = clickhouse(sql + '\nFORMAT JSONEachRow', params)
    return [json.loads(line) for line in text.splitlines() if line.strip()]


def filter_params(s):
    return {k: s[k] for k in ('date_from', 'date_to', 'region')}


def run_settings():
    context = get_current_context()
    conf = context['dag_run'].conf or {}
    return {
        'run_id': context['run_id'],
        'started_by': str(conf.get('started_by') or ''),
        'region': str(conf.get('region') or 'All'),
        'date_from': str(conf.get('date_from') or '2025-01-01'),
        'date_to': str(conf.get('date_to') or '2025-12-31'),
        'fail_at_step': int(str(conf.get('fail_at_step') or 0)),
    }


def record_run(s, status, orders=0, revenue=0.0, previous_revenue=0.0, failed_step='', message=''):
    clickhouse('INSERT INTO sample.job_runs', rows=[{
        'run_id': s['run_id'], 'started_by': s['started_by'], 'region': s['region'],
        'date_from': s['date_from'], 'date_to': s['date_to'], 'status': status,
        'failed_step': failed_step, 'message': message[:500], 'orders': orders, 'revenue': revenue,
        'previous_revenue': previous_revenue,
        'finished_at': pendulum.now('UTC').format('YYYY-MM-DD HH:mm:ss'),
    }])


def step(number, name, body, *args):
    """Wait, fail when the report asked for it, run the body; a failure is recorded in sample.job_runs."""
    s = run_settings()
    time.sleep(STEP_SECONDS)
    try:
        if s['fail_at_step'] == number:
            raise RuntimeError(f'Step {number} failed on purpose: the report parameter "Fail at step" is {number}')
        return body(s, *args)
    except Exception as e:
        record_run(s, 'failed', failed_step=name, message=str(e))
        raise


def check_parameters(s):
    for statement in TABLES:
        clickhouse(statement)
    date_from, date_to = date.fromisoformat(s['date_from']), date.fromisoformat(s['date_to'])
    if date_from > date_to:
        raise ValueError(f'The period starts after it ends: {date_from} - {date_to}')
    orders = select(f'SELECT count() AS orders FROM sample.sales WHERE {FILTER}', filter_params(s))[0]['orders']
    if int(orders) == 0:
        raise ValueError(f'No sales in region {s["region"]} from {date_from} to {date_to}')


def summarize_by_category(s):
    rows = select(f"""SELECT
    category,
    sum(p_orders) AS orders,
    round(sum(p_revenue), 2) AS revenue,
    argMax(product_name, p_revenue) AS top_product
FROM
(
    SELECT category, product_name, count() AS p_orders, sum(total_amount) AS p_revenue
    FROM sample.sales
    WHERE {FILTER}
    GROUP BY category, product_name
)
GROUP BY category
ORDER BY category""", filter_params(s))
    clickhouse('INSERT INTO sample.job_results', rows=[
        {'run_id': s['run_id'], 'category': r['category'], 'orders': int(r['orders']),
         'revenue': float(r['revenue']), 'top_product': r['top_product']} for r in rows])


def previous_period_revenue(s):
    date_from, date_to = date.fromisoformat(s['date_from']), date.fromisoformat(s['date_to'])
    previous_to = date_from - timedelta(days=1)
    previous_from = previous_to - (date_to - date_from)
    params = {**filter_params(s), 'date_from': previous_from.isoformat(), 'date_to': previous_to.isoformat()}
    return float(select(f'SELECT round(sum(total_amount), 2) AS revenue FROM sample.sales WHERE {FILTER}',
                        params)[0]['revenue'])


def publish_result(s, previous_revenue):
    totals = select("""SELECT sum(orders) AS orders, round(sum(revenue), 2) AS revenue
FROM sample.job_results
WHERE run_id = {run_id:String}""", {'run_id': s['run_id']})[0]
    record_run(s, 'success', int(totals['orders']), float(totals['revenue']), previous_revenue)


@dag(
    dag_id='datorio_demo_sales_summary',
    description='Started from a Datorio report: sales of a region and period by category',
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz='UTC'),
    catchup=False,
    tags=['datorio', 'example'],
)
def datorio_demo_sales_summary():

    @task(task_display_name='Check parameters')
    def check():
        step(1, 'Check parameters', check_parameters)

    @task(task_display_name='Summarize by category')
    def summarize():
        step(2, 'Summarize by category', summarize_by_category)

    @task(task_display_name='Compare with the previous period')
    def compare():
        return step(3, 'Compare with the previous period', previous_period_revenue)

    @task(task_display_name='Publish the result')
    def publish(previous_revenue):
        step(4, 'Publish the result', publish_result, previous_revenue)

    previous = compare()
    check() >> summarize() >> previous
    publish(previous)


datorio_demo_sales_summary()
