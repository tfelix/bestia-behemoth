-- Standing sessions: the token a client stores so that starting the game a second time does not
-- open a browser, bind a loopback port or ask for a passkey.
--
-- Opaque and stored as a digest, like every other token in this schema. It is deliberately not a
-- JWT: a signed token carries its own authority and so cannot be withdrawn before it expires, and
-- withdrawing it - on replay, on a ban, on recovery - is the reason the table exists at all.
CREATE TABLE refresh_token
(
    -- Same reasoning as authorization_code: a leak of this table yields hashes, not tokens.
    token_hash  VARCHAR(64) NOT NULL,
    account_id  BIGINT      NOT NULL,
    -- One unbroken chain of rotations, i.e. one device. Revocation works on the family rather than
    -- the account so that a replay on one machine does not sign the player out of their others.
    family_id   VARCHAR(64) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    -- Set when the token is rotated. A row with this set that is presented again is a copy.
    consumed_at DATETIME(6) NULL,
    revoked_at  DATETIME(6) NULL,
    PRIMARY KEY (token_hash),
    CONSTRAINT fk_refresh_token_account FOREIGN KEY (account_id) REFERENCES account (id)
) ENGINE = InnoDB;

CREATE INDEX idx_refresh_token_account ON refresh_token (account_id);
CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);
CREATE INDEX idx_refresh_token_expires ON refresh_token (expires_at);
