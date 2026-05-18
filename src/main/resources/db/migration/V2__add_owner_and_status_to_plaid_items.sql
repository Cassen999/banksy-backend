ALTER TABLE plaid_items
    ADD COLUMN owner_user_id UUID NOT NULL REFERENCES users(id),
    ADD COLUMN status        VARCHAR(20) NOT NULL DEFAULT 'HEALTHY';

CREATE INDEX idx_plaid_items_owner_user_id ON plaid_items(owner_user_id);
