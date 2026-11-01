-- ══════════════════════════════════════════════
--  Sample ClickHouse data for the quick start
-- ══════════════════════════════════════════════
-- Two years (2024-2025) of generated sales and web analytics. Every value comes from
-- cityHash64 of the row, so each installation gets the same numbers.

CREATE DATABASE IF NOT EXISTS sample;

-- Sample: Web analytics events
CREATE TABLE IF NOT EXISTS sample.page_views (
    event_date   Date,
    event_time   DateTime,
    user_id      UInt64,
    session_id   String,
    page_url     String,
    referrer     String,
    country      LowCardinality(String),
    device_type  LowCardinality(String),
    duration_sec UInt32
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_date)
ORDER BY (event_date, user_id, event_time);

-- Sample: Sales data
CREATE TABLE IF NOT EXISTS sample.sales (
    sale_date    Date,
    order_id     UInt64,
    product_name String,
    category     LowCardinality(String),
    region       LowCardinality(String),
    quantity     UInt32,
    unit_price   Float64,
    total_amount Float64
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(sale_date)
ORDER BY (sale_date, category, region);

-- Orders per day follow growth, season and weekday; the order id is the date and the order
-- number of the day (20240101001). Goods are 15% off on 24-30 November, one order in eight gets 10% off.
INSERT INTO sample.sales
SELECT sale_date, order_id, product_name, category, region, quantity, unit_price, total_amount
FROM
(
    WITH
        [('Laptop Pro 14', 'Electronics', 1299.00), ('Laptop Air 13', 'Electronics', 899.00),
         ('Smartphone X', 'Electronics', 699.00), ('Smartphone Lite', 'Electronics', 349.00),
         ('Tablet 11', 'Electronics', 449.00), ('Wireless Headphones', 'Electronics', 149.00),
         ('Smartwatch', 'Electronics', 249.00),
         ('Phone Case', 'Accessories', 19.99), ('USB-C Charger', 'Accessories', 29.99),
         ('USB-C Cable', 'Accessories', 12.99), ('Laptop Bag', 'Accessories', 59.99),
         ('Screen Protector', 'Accessories', 9.99), ('Wireless Mouse', 'Accessories', 24.99),
         ('Coffee Maker', 'Home', 89.00), ('Robot Vacuum', 'Home', 299.00),
         ('Air Purifier', 'Home', 179.00), ('Smart Speaker', 'Home', 79.00),
         ('Extended Warranty', 'Services', 59.00), ('Device Setup', 'Services', 39.00),
         ('Repair Service', 'Services', 99.00)] AS catalog,
        -- product numbers repeated by weight: accessories sell most often
        arrayFlatten(arrayMap((w, i) -> arrayWithConstant(w, i),
            [3, 4, 5, 6, 3, 6, 4, 9, 7, 9, 3, 8, 5, 4, 2, 2, 4, 5, 3, 2], arrayEnumerate(catalog))) AS product_pick,
        arrayFlatten([arrayWithConstant(3, 'North'), arrayWithConstant(2, 'South'), arrayWithConstant(3, 'East'),
                      arrayWithConstant(2, 'West'), arrayWithConstant(4, 'Central')]) AS regions
    SELECT
        sale_date,
        toUInt64(toYYYYMMDD(sale_date)) * 1000 + n + 1 AS order_id,
        product_pick[1 + cityHash64(order_id, 'product') % length(product_pick)] AS p,
        tupleElement(catalog[p], 1) AS product_name,
        tupleElement(catalog[p], 2) AS category,
        regions[1 + cityHash64(order_id, 'region') % length(regions)] AS region,
        toUInt32(multiIf(category = 'Accessories', 1 + cityHash64(order_id, 'qty') % 4,
                         category = 'Services', 1,
                         cityHash64(order_id, 'qty') % 10 = 0, 2, 1)) AS quantity,
        round(tupleElement(catalog[p], 3)
              * (1 - multiIf(toMonth(sale_date) = 11 AND toDayOfMonth(sale_date) >= 24 AND category != 'Services', 0.15,
                             cityHash64(order_id, 'discount') % 8 = 0, 0.10, 0)), 2) AS unit_price,
        round(quantity * unit_price, 2) AS total_amount
    FROM
    (
        SELECT
            toDate('2024-01-01') + number AS sale_date,
            toUInt32(round(45
                * [0.85, 0.8, 0.9, 0.95, 1.0, 0.95, 0.9, 0.95, 1.0, 1.05, 1.35, 1.6][toMonth(sale_date)]
                * [0.9, 0.9, 0.95, 1.0, 1.1, 1.25, 1.1][toDayOfWeek(sale_date)]
                * (1 + 0.3 * number / 730)
                * (0.85 + (cityHash64(number, 'noise') % 31) / 100))) AS orders
        FROM numbers(731)
    )
    ARRAY JOIN range(orders) AS n
);

-- Sessions per day follow growth, season and weekday; a session has 1-6 page views.
-- The landing page gets the external referrer, the next pages get the landing page.
INSERT INTO sample.page_views
SELECT event_date, event_time, user_id, session_id, page_url, referrer, country, device_type, duration_sec
FROM
(
    WITH
        ['/home', '/products', '/products/laptops', '/products/smartphones', '/products/home',
         '/pricing', '/blog', '/docs', '/cart', '/checkout', '/contact'] AS pages,
        arrayFlatten(arrayMap((w, i) -> arrayWithConstant(w, i),
            [6, 3, 2, 2, 0, 1, 2, 1, 0, 0, 0], arrayEnumerate(pages))) AS landing_pick,
        arrayFlatten(arrayMap((w, i) -> arrayWithConstant(w, i),
            [2, 4, 3, 3, 2, 2, 1, 1, 3, 1, 1], arrayEnumerate(pages))) AS next_pick,
        arrayFlatten(arrayMap((w, h) -> arrayWithConstant(w, h),
            [1, 1, 1, 1, 1, 2, 3, 5, 7, 8, 9, 9, 9, 9, 9, 9, 9, 10, 11, 12, 11, 9, 6, 3], range(24))) AS hour_pick,
        arrayFlatten([arrayWithConstant(10, 'google.com'), arrayWithConstant(6, ''), arrayWithConstant(2, 'bing.com'),
                      arrayWithConstant(2, 'github.com'), ['linkedin.com', 'twitter.com', 'reddit.com']]) AS referrers,
        arrayFlatten([arrayWithConstant(8, 'US'), arrayWithConstant(3, 'UK'), arrayWithConstant(3, 'DE'),
                      arrayWithConstant(3, 'IN'), arrayWithConstant(2, 'FR'), arrayWithConstant(2, 'PL'),
                      arrayWithConstant(2, 'BR'), arrayWithConstant(2, 'CA'), ['JP']]) AS countries,
        arrayFlatten([arrayWithConstant(11, 'desktop'), arrayWithConstant(8, 'mobile'), ['tablet']]) AS devices
    SELECT
        event_date,
        least(toDateTime(event_date) + start_hour * 3600 + cityHash64(sh, 'second') % 3600
                  + pg * (40 + cityHash64(sh, pg, 'gap') % 140),
              toDateTime(event_date) + 86399) AS event_time,
        1000 + cityHash64(sh, 'user') % 40000 AS user_id,
        concat('sess_', lower(hex(sh))) AS session_id,
        hour_pick[1 + cityHash64(sh, 'hour') % length(hour_pick)] AS start_hour,
        pages[landing_pick[1 + cityHash64(sh, 'landing') % length(landing_pick)]] AS landing_url,
        if(pg = 0, landing_url, pages[next_pick[1 + cityHash64(sh, pg, 'page') % length(next_pick)]]) AS page_url,
        if(pg = 0, referrers[1 + cityHash64(sh, 'referrer') % length(referrers)], landing_url) AS referrer,
        countries[1 + cityHash64(sh, 'country') % length(countries)] AS country,
        devices[1 + cityHash64(sh, 'device') % length(devices)] AS device_type,
        toUInt32(5 + cityHash64(sh, pg, 'duration') % 60 + cityHash64(sh, pg, 'read') % 180) AS duration_sec
    FROM
    (
        SELECT
            event_date,
            cityHash64(day, s) AS sh,
            [1, 1, 1, 2, 2, 3, 3, 4, 5, 6][1 + cityHash64(day, s, 'pages') % 10] AS page_count
        FROM
        (
            SELECT
                number AS day,
                toDate('2024-01-01') + number AS event_date,
                toUInt32(round(150
                    * [0.9, 0.85, 0.95, 0.95, 1.0, 0.95, 0.9, 0.95, 1.0, 1.05, 1.25, 1.35][toMonth(event_date)]
                    * [0.95, 0.95, 1.0, 1.0, 1.05, 1.1, 1.0][toDayOfWeek(event_date)]
                    * (1 + 0.4 * number / 730)
                    * (0.9 + (cityHash64(number, 'noise') % 21) / 100))) AS sessions
            FROM numbers(731)
        )
        ARRAY JOIN range(sessions) AS s
    )
    ARRAY JOIN range(page_count) AS pg
);
