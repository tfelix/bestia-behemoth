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
    journey_ended     BIT(1)      NOT NULL,
    letter_arrived    BIT(1)      NOT NULL,
    parcel_arrived    BIT(1)      NOT NULL,
    bestia_stopped    BIT(1)      NOT NULL,
    bestia_attacked   BIT(1)      NOT NULL,
    -- Minutes after midnight in time_zone; both null for no quiet hours. May wrap past midnight.
    quiet_from        SMALLINT    NULL,
    quiet_to          SMALLINT    NULL,
    time_zone         VARCHAR(64) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (account_id)
) ENGINE = InnoDB;

-- A bug report from the game client, kept here until it is published as an issue in the private reports repo.
CREATE TABLE bug_report
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    account_id      BIGINT      NOT NULL,
    -- As the client named it: only a hint for whoever reads the issue.
    master_id       BIGINT      NULL,
    kind            VARCHAR(16) NOT NULL,
    -- Hex SHA-256 of a crash's exception type and top frames; equal signatures are one crash.
    signature       CHAR(64)    NULL,
    status          VARCHAR(16) NOT NULL,
    client_version  VARCHAR(64) NULL,
    -- Null for a shadow-banned sender, whose report is answered but not kept.
    text            TEXT        NULL,
    issue_number    INT         NULL,
    attempts        INT         NOT NULL,
    next_attempt_at DATETIME(6) NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE INDEX idx_bug_report_account_created ON bug_report (account_id, created_at);
CREATE INDEX idx_bug_report_status ON bug_report (status, next_attempt_at);

-- The files of a report not yet published; deleted once they are committed to the reports repo.
CREATE TABLE bug_report_file
(
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    report_id BIGINT      NOT NULL,
    name      VARCHAR(64) NOT NULL,
    content   LONGBLOB    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_bug_report_file_name UNIQUE (report_id, name),
    CONSTRAINT fk_bug_report_file_report FOREIGN KEY (report_id) REFERENCES bug_report (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- Accounts whose reports are answered as if accepted, and then dropped.
CREATE TABLE bug_report_ban
(
    account_id BIGINT       NOT NULL,
    reason     VARCHAR(255) NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (account_id)
) ENGINE = InnoDB;
