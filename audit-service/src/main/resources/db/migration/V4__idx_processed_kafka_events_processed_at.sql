-- Supports nightly DELETE WHERE processed_at < :cutoff without a full table scan
CREATE INDEX idx_processed_kafka_events_processed_at ON processed_kafka_events (processed_at);
