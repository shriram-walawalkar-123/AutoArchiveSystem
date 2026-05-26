CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    file_path     VARCHAR(2048) NOT NULL,
    action_type   VARCHAR(50)   NOT NULL,
    status        VARCHAR(50)   NOT NULL,
    bytes_freed   BIGINT        DEFAULT 0,
    error_message TEXT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
