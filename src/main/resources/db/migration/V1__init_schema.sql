CREATE TABLE users (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name  VARCHAR(100)  NOT NULL,
    last_name   VARCHAR(100)  NOT NULL,
    username    VARCHAR(50)   NOT NULL UNIQUE,
    email       VARCHAR(255)  NOT NULL UNIQUE,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE oauth_identities (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider          VARCHAR(50)  NOT NULL,
    provider_user_id  VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_oauth_identities_user_id ON oauth_identities(user_id);

CREATE TABLE plaid_items (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    access_token_enc    TEXT          NOT NULL,
    item_id             VARCHAR(255)  NOT NULL UNIQUE,
    institution_id      VARCHAR(255)  NOT NULL,
    institution_name    VARCHAR(255)  NOT NULL,
    transaction_cursor  TEXT,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE plaid_accounts (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    plaid_item_id     UUID          NOT NULL REFERENCES plaid_items(id) ON DELETE CASCADE,
    plaid_account_id  VARCHAR(255)  NOT NULL UNIQUE,
    name              VARCHAR(255)  NOT NULL,
    official_name     VARCHAR(255),
    type              VARCHAR(50)   NOT NULL,
    subtype           VARCHAR(50),
    mask              VARCHAR(4),
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_plaid_accounts_item_id ON plaid_accounts(plaid_item_id);

CREATE TABLE user_plaid_items (
    user_id        UUID       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plaid_item_id  UUID       NOT NULL REFERENCES plaid_items(id) ON DELETE CASCADE,
    added_at       TIMESTAMP  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, plaid_item_id)
);

CREATE INDEX idx_user_plaid_items_user_id       ON user_plaid_items(user_id);
CREATE INDEX idx_user_plaid_items_plaid_item_id ON user_plaid_items(plaid_item_id);
