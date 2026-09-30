-- V8: pharmacy configuration settings and JWT revocation (logout) list
CREATE TABLE system_settings
(
    setting_key VARCHAR(100)  NOT NULL,
    value       VARCHAR(500)  NOT NULL,
    updated_by  BIGINT        NULL,
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (setting_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE revoked_tokens
(
    jti        VARCHAR(36) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NOT NULL,
    PRIMARY KEY (jti)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_revoked_tokens_expires ON revoked_tokens (expires_at);
