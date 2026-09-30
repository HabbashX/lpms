-- V1: users (no public registration; only administrators create users)
CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    username              VARCHAR(50)  NOT NULL,
    password              VARCHAR(100) NOT NULL,
    role                  VARCHAR(20)  NOT NULL,
    enabled               BIT(1)       NOT NULL DEFAULT b'1',
    must_change_password  BIT(1)       NOT NULL DEFAULT b'0',
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          DATETIME(6)  NULL,
    token_not_before      DATETIME(6)  NULL,
    last_login_at         DATETIME(6)  NULL,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'PHARMACIST', 'EMPLOYEE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_last_login ON users (last_login_at);
