CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE retention_policies (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    rules       JSONB        NOT NULL,
    region      VARCHAR(50)  NOT NULL DEFAULT 'LOCAL',
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_retention_policies_region ON retention_policies(region);
CREATE INDEX idx_retention_policies_active_region ON retention_policies(region) WHERE is_active = TRUE;
CREATE INDEX idx_retention_policies_rules_gin ON retention_policies USING GIN (rules);

-- Seed a minimal default policy so the engine has a DB rule to read.
-- You can edit this JSONB later to add extensions/minSize/etc.
INSERT INTO retention_policies (name, description, rules, region, is_active)
VALUES (
  'default-local',
  'Default local retention policy (v1)',
  '{"archiveAfterDays": 0}'::jsonb,
  'LOCAL',
  TRUE
);
