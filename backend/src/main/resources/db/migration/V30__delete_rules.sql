-- Deleting a data source or a saved query failed with a foreign key error once a query had run on it:
-- the execution log kept a reference. The log is history, so it keeps its rows with an empty reference.
ALTER TABLE dl_query_execution DROP CONSTRAINT IF EXISTS dl_query_execution_datasource_id_fkey;
ALTER TABLE dl_query_execution
    ADD CONSTRAINT dl_query_execution_datasource_id_fkey
    FOREIGN KEY (datasource_id) REFERENCES dl_datasource(id) ON DELETE SET NULL;

ALTER TABLE dl_query_execution DROP CONSTRAINT IF EXISTS dl_query_execution_saved_query_id_fkey;
ALTER TABLE dl_query_execution
    ADD CONSTRAINT dl_query_execution_saved_query_id_fkey
    FOREIGN KEY (saved_query_id) REFERENCES dl_query(id) ON DELETE SET NULL;
