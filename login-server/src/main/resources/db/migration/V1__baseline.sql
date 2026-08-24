-- Baseline of the schema login-server ran on before it had any migrations at all: it used
-- `ddl-auto: create` against an in-memory H2 and threw every account away on restart. The tables
-- below are what Hibernate generated from the existing entities, transcribed so that MariaDB is
-- now the source of truth and `ddl-auto: validate` has something to check against.

CREATE TABLE account
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    role         VARCHAR(20) NOT NULL,
    -- Persisted as a name, not an ordinal. The entity previously omitted @Enumerated, so this
    -- column held an ordinal int whose meaning changed if anyone reordered AccountStatus. That was
    -- invisible while the database was wiped on every boot and is a live hazard now that it is not.
    status       VARCHAR(20) NOT NULL,
    banned_until DATETIME(6) NULL,
    last_login   DATETIME(6) NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE INDEX idx_account_status ON account (status);

CREATE TABLE nft_login_method
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    account_id   BIGINT      NOT NULL,
    nft_token_id BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    last_used_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_nft_login_method_token_id UNIQUE (nft_token_id),
    CONSTRAINT fk_nft_login_method_account FOREIGN KEY (account_id) REFERENCES account (id)
) ENGINE = InnoDB;

CREATE TABLE static_token_login_method
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    account_id   BIGINT       NOT NULL,
    username     VARCHAR(64)  NOT NULL,
    static_token VARCHAR(128) NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    last_used_at DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_static_token_login_method_username UNIQUE (username),
    CONSTRAINT fk_static_token_login_method_account FOREIGN KEY (account_id) REFERENCES account (id)
) ENGINE = InnoDB;
