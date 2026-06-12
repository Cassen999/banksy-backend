CREATE TABLE user_rejected_categories (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category   VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (user_id, category)
);
