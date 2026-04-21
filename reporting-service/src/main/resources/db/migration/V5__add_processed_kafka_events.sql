CREATE TABLE IF NOT EXISTS processed_kafka_events (
    id             UUID                     NOT NULL PRIMARY KEY,
    event_id       UUID                     NOT NULL,
    consumer_group VARCHAR(255)             NOT NULL,
    processed_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uidx_processed_kafka_events_event_consumer
    ON processed_kafka_events (event_id, consumer_group);
