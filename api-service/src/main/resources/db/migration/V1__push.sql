-- Where the companion app's notifications go: one row per browser or installed app that allowed them.
CREATE TABLE push_subscription
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    account_id    BIGINT        NOT NULL,
    -- The push service's URL for this browser. Hashed for the unique key, because it is too long to index whole.
    endpoint      VARCHAR(1024) NOT NULL,
    endpoint_hash CHAR(64)      NOT NULL,
    -- The browser's public key and auth secret (RFC 8291), base64url as the browser hands them over.
    p256dh        VARCHAR(128)  NOT NULL,
    auth          VARCHAR(64)   NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    last_ok_at    DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_subscription_endpoint UNIQUE (endpoint_hash)
) ENGINE = InnoDB;

CREATE INDEX idx_push_subscription_account ON push_subscription (account_id);

-- What an account wants to be woken for. No row: every category on, no quiet hours.
CREATE TABLE push_preference
(
    account_id        BIGINT      NOT NULL,
    home_fell         BIT(1)      NOT NULL,
    bestia_died       BIT(1)      NOT NULL,
    construction_done BIT(1)      NOT NULL,
    -- Minutes after midnight in time_zone; both null for no quiet hours. May wrap past midnight.
    quiet_from        SMALLINT    NULL,
    quiet_to          SMALLINT    NULL,
    time_zone         VARCHAR(64) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (account_id)
) ENGINE = InnoDB;
