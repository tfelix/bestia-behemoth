-- When the session passed its passkey ceremony. Enrolling a further passkey is only allowed shortly after that:
-- adding a credential is the step that survives every later revocation, so it needs a fresh sign-in.
ALTER TABLE login_session
    ADD COLUMN authenticated_at DATETIME(6) NULL;
