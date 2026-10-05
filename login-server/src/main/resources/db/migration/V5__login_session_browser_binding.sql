-- The browser half of a game login is bound to the browser that first opened the link: a digest of the
-- cookie that page load set. Whoever started the login also knows the session id, so the id alone must not be
-- enough to drive the ceremony, finish it or enrol a passkey.
ALTER TABLE login_session
    ADD COLUMN browser_binding_hash VARCHAR(64) NULL;
