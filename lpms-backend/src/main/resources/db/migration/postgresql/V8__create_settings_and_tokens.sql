-- V8: pharmacy configuration settings and JWT revocation (logout) list
CREATE TABLE system_settings
(
    setting_key VARCHAR(100)  NOT NULL,
    value       VARCHAR(500)  NOT NULL,
    updated_by  BIGINT        NULL,
    updated_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key)
);

CREATE TABLE revoked_tokens
(
    jti        VARCHAR(36)   NOT NULL,
    expires_at TIMESTAMP(6)  NOT NULL,
    revoked_at TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (jti)
);

CREATE INDEX idx_revoked_tokens_expires ON revoked_tokens (expires_at);
