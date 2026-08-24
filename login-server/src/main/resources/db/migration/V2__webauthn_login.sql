-- Passkey (WebAuthn) login. Adds the account-facing identity an account never had, the credential
-- table, the server-side challenge store, and the browser-to-game handoff (login session +
-- single-use authorization code).
--
-- BOOLEAN columns are declared BIT(1) on purpose: Hibernate's MariaDB dialect emits `bit` for
-- java.lang.Boolean, and `ddl-auto: validate` compares the type name it would have generated
-- against the one the database reports.
--
-- The *_hash columns hold base64url SHA-256 rather than raw bytes. A textual key avoids making an
-- array the JPA identifier, and costs nothing: these are opaque either way.

-- Names the account rather than the player: it labels the credential in the passkey picker and is
-- what recovery is looked up by. The name other players see is the master's, which lives on the
-- zone. Nullable because the accounts that already exist (NFT, static dev token) have none; it is
-- required for every account created through the passkey flow.
ALTER TABLE account
    ADD COLUMN display_name VARCHAR(32) NULL;

CREATE UNIQUE INDEX uq_account_display_name ON account (display_name);

-- One WebAuthn identity per account, shared by every credential registered to it. This is what
-- makes a synced passkey created on one machine resolve to the same account on another: the
-- authenticator returns this handle, not anything device-specific.
CREATE TABLE webauthn_user
(
    account_id  BIGINT        NOT NULL,
    -- 32 random bytes. Never the account id and never anything derived from the display name:
    -- the spec caps the handle at 64 bytes and forbids putting personal data in it, and a
    -- sequential value would leak account ordering to any relying party the user visits.
    user_handle VARBINARY(64) NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (account_id),
    CONSTRAINT uq_webauthn_user_handle UNIQUE (user_handle),
    CONSTRAINT fk_webauthn_user_account FOREIGN KEY (account_id) REFERENCES account (id)
) ENGINE = InnoDB;

CREATE TABLE webauthn_credential
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    account_id      BIGINT         NOT NULL,
    -- The spec allows credential ids up to 1023 bytes, but everything that actually ships stays
    -- well under 255 and MariaDB will not index a longer unique column under the default charset.
    credential_id   VARBINARY(255) NOT NULL,
    -- The COSE_Key encoding, not a raw EC point: the algorithm has to survive alongside the key.
    public_key_cose VARBINARY(1024) NOT NULL,
    signature_count BIGINT         NOT NULL DEFAULT 0,
    -- Authenticator model. Only present in the registration response, so it cannot be backfilled
    -- later without re-enrolling the credential.
    aaguid          VARBINARY(16)  NULL,
    transports      VARCHAR(64)    NULL,
    -- BE: fixed for the life of the credential, and the only reliable way to tell a synced passkey
    -- from a device-bound one. BS: the current backup state, which moves over time.
    backup_eligible BIT(1)         NOT NULL,
    backup_state    BIT(1)         NOT NULL,
    uv_initialized  BIT(1)         NOT NULL,
    discoverable    BIT(1)         NULL,
    label           VARCHAR(64)    NULL,
    created_at      DATETIME(6)    NOT NULL,
    last_used_at    DATETIME(6)    NULL,
    PRIMARY KEY (id),
    -- Global, not per-account: a credential already claimed by one account must never be
    -- registrable against another.
    CONSTRAINT uq_webauthn_credential_id UNIQUE (credential_id),
    CONSTRAINT fk_webauthn_credential_account FOREIGN KEY (account_id) REFERENCES account (id)
) ENGINE = InnoDB;

CREATE INDEX idx_webauthn_credential_account ON webauthn_credential (account_id);

-- Challenges live here and nowhere else. Handing the challenge to the browser and taking it back
-- on the finish call would let a caller pick its own, which defeats the whole ceremony.
CREATE TABLE webauthn_ceremony
(
    id               VARCHAR(64)   NOT NULL,
    ceremony_type    VARCHAR(16)   NOT NULL,
    -- The serialised PublicKeyCredentialCreationOptions / AssertionRequest the library produced.
    request_json     LONGTEXT      NOT NULL,
    login_session_id_hash VARCHAR(64)  NULL,
    -- Set only while registering a brand new account: no account row exists yet at this point, so
    -- the chosen name and the handle it will get are parked here until the credential verifies.
    pending_name     VARCHAR(32)   NULL,
    pending_handle   VARBINARY(64) NULL,
    -- Set only when adding a credential to an account that already exists.
    account_id       BIGINT        NULL,
    -- Recovery replaces the account's whole code set once the new passkey verifies, so the intent
    -- has to survive from the start of the ceremony through to its end.
    reissue_recovery_codes BIT(1)  NOT NULL DEFAULT b'0',
    created_at       DATETIME(6)   NOT NULL,
    expires_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE INDEX idx_webauthn_ceremony_expires ON webauthn_ceremony (expires_at);

CREATE TABLE login_session
(
    -- Base64url SHA-256 of the identifier that travels in the browser URL, so a database leak does
    -- not hand out resumable login sessions.
    id_hash          VARCHAR(64)  NOT NULL,
    redirect_uri     VARCHAR(255) NOT NULL,
    code_challenge   VARCHAR(128) NOT NULL,
    challenge_method VARCHAR(8)   NOT NULL,
    client_state     VARCHAR(128) NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    account_id       BIGINT       NULL,
    created_at       DATETIME(6)  NOT NULL,
    expires_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id_hash)
) ENGINE = InnoDB;

CREATE INDEX idx_login_session_expires ON login_session (expires_at);

CREATE TABLE authorization_code
(
    -- Same reasoning as login_session: the code itself is never written down. A leak of this table
    -- yields hashes of codes that expired a minute after they were minted.
    code_hash             VARCHAR(64) NOT NULL,
    login_session_id_hash VARCHAR(64) NOT NULL,
    account_id            BIGINT      NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    expires_at            DATETIME(6) NOT NULL,
    consumed_at           DATETIME(6) NULL,
    PRIMARY KEY (code_hash),
    CONSTRAINT fk_authorization_code_account FOREIGN KEY (account_id) REFERENCES account (id)
) ENGINE = InnoDB;

CREATE INDEX idx_authorization_code_expires ON authorization_code (expires_at);

-- The only way back into an account whose passkeys are all gone. There is no email on file, so if
-- these are lost too the account is unrecoverable, and the page that issues them says so.
CREATE TABLE recovery_code
(
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    account_id BIGINT        NOT NULL,
    -- A plain SHA-256 rather than a password hash: these are 128 bits of CSPRNG output, so there is
    -- no dictionary to run against them and nothing for a work factor to buy.
    code_hash  VARCHAR(64)   NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    used_at    DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recovery_code_account FOREIGN KEY (account_id) REFERENCES account (id)
) ENGINE = InnoDB;

CREATE INDEX idx_recovery_code_account ON recovery_code (account_id);
