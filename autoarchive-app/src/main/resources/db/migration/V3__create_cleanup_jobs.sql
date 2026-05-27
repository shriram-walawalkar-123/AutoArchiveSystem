CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE cleanup_jobs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    policy_id        UUID NOT NULL REFERENCES retention_policies(id),
    job_name         VARCHAR(150) NOT NULL UNIQUE,
    cron_expression  VARCHAR(100) NOT NULL,
    timezone         VARCHAR(100) NOT NULL DEFAULT 'UTC',
    last_run_at      TIMESTAMPTZ,
    next_run_at      TIMESTAMPTZ,
    status           VARCHAR(50) NOT NULL DEFAULT 'IDLE',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cleanup_jobs_next_run ON cleanup_jobs(next_run_at);

-- Seed one default job tied to the default policy.
INSERT INTO cleanup_jobs (policy_id, job_name, cron_expression, timezone, next_run_at, status)
SELECT
  rp.id,
  'default-local-job',
  '*/30 * * * * *',
  'UTC',
  CURRENT_TIMESTAMP,
  'IDLE'
FROM retention_policies rp
WHERE rp.name = 'default-local'
LIMIT 1;

