-- V10: rotating, single-use refresh tokens. Only the SHA-256 hash is stored, so a
-- database leak cannot be replayed as a session.
CREATE TABLE refresh_tokens
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6)  NOT NULL,
    revoked_at DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Revoking every session of a user on token-theft detection scans by user.
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);