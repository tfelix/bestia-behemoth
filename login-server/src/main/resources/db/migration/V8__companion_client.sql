-- Which kind of client a login and a standing session belong to: the game, or the companion web app. A code
-- minted for one is never redeemed by the other, and a refresh token only ever yields its own client's tokens,
-- so a companion app's token can never be turned into a zone login.
ALTER TABLE login_session ADD COLUMN client VARCHAR(16) NOT NULL DEFAULT 'GAME';
ALTER TABLE refresh_token ADD COLUMN client VARCHAR(16) NOT NULL DEFAULT 'GAME';
