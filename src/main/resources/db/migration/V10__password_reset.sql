CREATE TABLE password_reset (
    id          CHAR(36) PRIMARY KEY,
    user_id     CHAR(36) NOT NULL UNIQUE,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  DATETIME NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);