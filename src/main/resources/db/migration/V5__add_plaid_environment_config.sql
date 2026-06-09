CREATE TABLE plaid_environment_config (
    id         SERIAL PRIMARY KEY,
    env        VARCHAR(20) NOT NULL DEFAULT 'sandbox',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO plaid_environment_config (env) VALUES ('sandbox');
