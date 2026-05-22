CREATE TABLE notifications (
    id          UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message     TEXT      NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    read        BOOLEAN   NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
