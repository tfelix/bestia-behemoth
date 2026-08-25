-- The username + static token login is gone. It existed so the game client had a way in before
-- passkeys worked; the `dev` profile now grants a raised `account.sign-up-role` instead, so an
-- ordinary passkey registration on a dev host is the elevated account.
--
-- The accounts the seeder created are left behind on purpose. Dropping the login method does not
-- make them unreachable in a way worth cleaning up: they have no credential of their own, so they
-- are simply inert rows, and deleting them would take their masters on the zone with them.

DROP TABLE IF EXISTS static_token_login_method;
