-- Every ban and kick a GM made, and why. Written in the same transaction as the action itself, so no action
-- happens without its row.
CREATE TABLE gm_action
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    actor_account_id  BIGINT       NOT NULL,
    target_account_id BIGINT       NOT NULL,
    action            VARCHAR(16)  NOT NULL,
    note              VARCHAR(255) NOT NULL,
    -- Set for a ban that ends; null for a kick and for a permanent ban.
    banned_until      DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_gm_action_actor FOREIGN KEY (actor_account_id) REFERENCES account (id),
    CONSTRAINT fk_gm_action_target FOREIGN KEY (target_account_id) REFERENCES account (id)
) ENGINE = InnoDB;

CREATE INDEX idx_gm_action_target ON gm_action (target_account_id);
