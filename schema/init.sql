-- TODO Once finished put the Schema to some other repository, here just for bootstrapping the development.

\c vehicles

SET ROLE creator;

CREATE TABLE alert (
    id                    BIGSERIAL PRIMARY KEY,
    created_at            TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    modified_at           TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    route_id              TEXT,
    stop_id               TEXT,
    affects_all_routes    BOOLEAN,
    affects_all_stops     BOOLEAN,
    valid_from            TIMESTAMPTZ,
    valid_to              TIMESTAMPTZ,
    last_modified         TIMESTAMPTZ,
    json_schema_version   SMALLINT DEFAULT 1,
    data                  jsonb,
    ext_id_bulletin       TEXT
);

ALTER TABLE alert ADD CONSTRAINT unique_alert_route_constraint UNIQUE (route_id, valid_from, valid_to, last_modified);
ALTER TABLE alert ADD CONSTRAINT unique_alert_stop_constraint UNIQUE (stop_id, valid_from, valid_to, last_modified);
ALTER TABLE alert ADD CONSTRAINT unique_alert_all_routes_all_stops_constraint UNIQUE (affects_all_routes, affects_all_stops, valid_from, valid_to, last_modified);

CREATE INDEX alert_route_idx ON alert(valid_from, valid_to, route_id);
CREATE INDEX alert_stop_idx ON alert(valid_from, valid_to, stop_id);
CREATE INDEX alert_all_routes_idx ON alert(valid_from, valid_to, affects_all_routes);
CREATE INDEX alert_all_stops_idx ON alert(valid_from, valid_to, affects_all_stops);

GRANT INSERT, UPDATE ON TABLE alert TO hfp_writer;
GRANT SELECT ON TABLE alert TO PUBLIC;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA PUBLIC TO hfp_writer;

-- SELECT create_hypertable('alert', 'created_at', if_not_exists => true);
-- SELECT add_dimension('alert', 'start_datetime', chunk_time_interval => interval '1 day', if_not_exists => true);
-- SELECT add_dimension('alert', 'end_datetime', chunk_time_interval => interval '1 day', if_not_exists => true);

