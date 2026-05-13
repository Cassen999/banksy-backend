-- BanksyDB Schema
-- PostgreSQL 13+
-- Run this in pgAdmin via Query Tool: Tools > Query Tool, paste, then Execute (F5)
--
-- gen_random_uuid() is built-in as of PostgreSQL 13.
-- If you are on PostgreSQL 12 or earlier, uncomment the line below:
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- ...and replace every gen_random_uuid() with uuid_generate_v4()


-- -----------------------------------------------------
-- users
-- Core identity table. No password — auth is handled by OAuth.
-- -----------------------------------------------------
CREATE TABLE users (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name  VARCHAR(100)  NOT NULL,
    last_name   VARCHAR(100)  NOT NULL,
    username    VARCHAR(50)   NOT NULL UNIQUE,
    email       VARCHAR(255)  NOT NULL UNIQUE,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);


-- -----------------------------------------------------
-- oauth_identities
-- Links a user to one or more OAuth providers (e.g. Google).
-- Unique constraint on (provider, provider_user_id) prevents
-- the same Google account from being linked twice.
-- -----------------------------------------------------
CREATE TABLE oauth_identities (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider          VARCHAR(50)  NOT NULL,
    provider_user_id  VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_oauth_identities_user_id ON oauth_identities(user_id);


-- -----------------------------------------------------
-- plaid_items
-- One row = one bank connection (a Plaid "Item").
-- access_token_enc stores the Plaid access token encrypted
-- with AES-256-GCM at the application level.
-- -----------------------------------------------------
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


-- -----------------------------------------------------
-- plaid_accounts
-- One row = one account within a bank connection.
-- A single plaid_item can have multiple accounts
-- (e.g. Chase checking + Chase savings).
-- mask (last 4 digits) is safe to store in plaintext.
-- -----------------------------------------------------
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


-- -----------------------------------------------------
-- user_plaid_items
-- Junction table. Grants a user access to a bank connection.
-- This is the shared account mechanism: if two users share a
-- bank account, both have a row here pointing to the same
-- plaid_item. One access_token, no duplicated credentials.
-- -----------------------------------------------------
CREATE TABLE user_plaid_items (
    user_id        UUID       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plaid_item_id  UUID       NOT NULL REFERENCES plaid_items(id) ON DELETE CASCADE,
    added_at       TIMESTAMP  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, plaid_item_id)
);

CREATE INDEX idx_user_plaid_items_user_id       ON user_plaid_items(user_id);
CREATE INDEX idx_user_plaid_items_plaid_item_id ON user_plaid_items(plaid_item_id);
