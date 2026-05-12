CREATE TABLE IF NOT EXISTS decisions (
    tx_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    risk_score SMALLINT NOT NULL,
    model_version VARCHAR(16),
    ml_risk_score SMALLINT DEFAULT 0,
    ml_fallback BOOLEAN DEFAULT false,
    rules_fired JSONB,
    features_snapshot JSONB,
    shadow_decision VARCHAR(16),
    latency_ms INTEGER,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_decisions_user_id ON decisions(user_id);
CREATE INDEX idx_decisions_created_at ON decisions(created_at);

CREATE TABLE IF NOT EXISTS rules (
    rule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(128) NOT NULL,
    condition JSONB NOT NULL,
    action JSONB NOT NULL,
    enabled BOOLEAN DEFAULT true,
    updated_by VARCHAR(64),
    version INT DEFAULT 1
);