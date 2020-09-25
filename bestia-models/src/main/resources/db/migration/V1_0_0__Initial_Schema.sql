SET FOREIGN_KEY_CHECKS=0;

DROP TABLE IF EXISTS `accounts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `accounts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `additional_bestia_slots` int(11) DEFAULT 0,
  `username` varchar(32) COLLATE utf8_bin NOT NULL,
  `is_activated` bit(1) NOT NULL,
  `login_token` varchar(255) COLLATE utf8_bin DEFAULT "",
  `register_date` date DEFAULT NULL,
  `account_type` varchar(20) COLLATE utf8_bin NOT NULL,
  `party_id` bigint(20) DEFAULT NULL,
  `gender` varchar(10) COLLATE utf8_bin NOT NULL,
  `hairstyle` varchar(10) COLLATE utf8_bin NOT NULL,
  `banned_until` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_username` (`username`),
  KEY `FK_account_party_id` (`party_id`),
  CONSTRAINT `FK_account_party_id` FOREIGN KEY (`party_id`) REFERENCES `parties` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `basic_logins`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `basic_logins` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `email` varchar(64) COLLATE utf8_bin NOT NULL,
  `password` varchar(255) COLLATE utf8_bin NOT NULL,
  `account_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_email` (`email`),
  KEY `FK_basic_logins_account_id` (`account_id`),
  CONSTRAINT `FK_basic_logins_account_id` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `attacks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `attacks` (
  `id` bigint(20) NOT NULL,
  `animation` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `based_status` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `casttime` int(11) NOT NULL,
  `cooldown` int(11) NOT NULL,
  `attack_db_name` varchar(255) COLLATE utf8_bin NOT NULL,
  `element` varchar(255) COLLATE utf8_bin NOT NULL,
  `indicator` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `needs_line_of_sight` bit(1) NOT NULL,
  `mana_cost` int(11) NOT NULL,
  `atk_range` bigint(20) NOT NULL,
  `strength` int(11) NOT NULL,
  `target` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `has_script` bit(1) NOT NULL,
  `type` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_attacks_attack_db_name` (`attack_db_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `bestia_attacks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `bestia_attacks` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `min_level` int(11) NOT NULL,
  `attack_id` bigint(20) NOT NULL,
  `bestia_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_bestia_attacks_attack_id_bestia_id` (`attack_id`,`bestia_id`),
  KEY `FKhkh96eh7359axliwg1kw0efoh` (`bestia_id`),
  CONSTRAINT `FK_bestia_attacks_bestia_id` FOREIGN KEY (`bestia_id`) REFERENCES `bestias` (`id`),
  CONSTRAINT `FK_bestia_attacks_attacks_id` FOREIGN KEY (`attack_id`) REFERENCES `attacks` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `bestias`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `bestias` (
  `id` bigint(20) NOT NULL,
  `bestia_db_name` varchar(100) COLLATE utf8_bin NOT NULL,
  `default_name` varchar(100) COLLATE utf8_bin NOT NULL,
  `element` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `exp_gained` int(11) NOT NULL,
  `is_boss` bit(1) NOT NULL,
  `level` int(11) NOT NULL,
  `script_exec` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `mesh` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `type` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `temperature_kind` varchar(50) COLLATE utf8_bin NOT NULL,
  `b_hp` int(11) NOT NULL DEFAULT 0,
  `b_mana` int(11) NOT NULL DEFAULT 0,
  `b_stamina` int(11) NOT NULL DEFAULT 0,
  `b_str` int(11) NOT NULL DEFAULT 0,
  `b_vit` int(11) NOT NULL DEFAULT 0,
  `b_int` int(11) NOT NULL DEFAULT 0,
  `b_will` int(11) NOT NULL DEFAULT 0,
  `b_agi` int(11) NOT NULL DEFAULT 0,
  `b_dex` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_bestia_bestia_db_name` (`bestia_db_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

CREATE TABLE `player_bestias` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `current_health` int(11) NOT NULL,
  `current_mana` int(11) NOT NULL,
  `current_stamina` int(11) NOT NULL,
  `max_health` int(11) NOT NULL,
  `max_mana` int(11) NOT NULL,
  `max_stamina` int(11) NOT NULL,
  `x` bigint(20) NOT NULL,
  `y` bigint(20) NOT NULL,
  `z` bigint(20) NOT NULL,
  `ev_hp` int(11) NOT NULL DEFAULT 0,
  `ev_mana` int(11) NOT NULL DEFAULT 0,
  `ev_stamina` int(11) NOT NULL DEFAULT 0,
  `ev_str` int(11) NOT NULL DEFAULT 0,
  `ev_vit` int(11) NOT NULL DEFAULT 0,
  `ev_int` int(11) NOT NULL DEFAULT 0,
  `ev_will` int(11) NOT NULL DEFAULT 0,
  `ev_agi` int(11) NOT NULL DEFAULT 0,
  `ev_dex` int(11) NOT NULL DEFAULT 0,
  `entity_id` bigint(20) NOT NULL DEFAULT 0,
  `exp` bigint(20) NOT NULL,
  `gender` varchar(10) DEFAULT NULL,
  `iv_hp` int(11) NOT NULL DEFAULT 0,
  `iv_mana` int(11) NOT NULL DEFAULT 0,
  `iv_stamina` int(11) NOT NULL DEFAULT 0,
  `iv_str` int(11) NOT NULL DEFAULT 0,
  `iv_vit` int(11) NOT NULL DEFAULT 0,
  `iv_int` int(11) NOT NULL DEFAULT 0,
  `iv_will` int(11) NOT NULL DEFAULT 0,
  `iv_agi` int(11) NOT NULL DEFAULT 0,
  `iv_dex` int(11) NOT NULL DEFAULT 0,
  `level` int(11) NOT NULL DEFAULT 0,
  `name` varchar(255) DEFAULT NULL,
  `save_x` bigint(20) NOT NULL DEFAULT 0,
  `save_y` bigint(20) NOT NULL DEFAULT 0,
  `save_z` bigint(20) NOT NULL DEFAULT 0,
  `master_id` bigint(20) DEFAULT NULL,
  `bestia_id` bigint(20) NOT NULL,
  `account_id` bigint(20) NOT NULL,
  `party_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `key_player_bestias_master_id` (`master_id`),
  KEY `fk_player_bestias_bestia_id` (`bestia_id`),
  KEY `fk_player_bestias_account_id` (`account_id`),
  KEY `fk_player_bestias_party_id` (`party_id`),
  CONSTRAINT `player_bestias_account_id` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`),
  CONSTRAINT `player_bestias_party_id` FOREIGN KEY (`party_id`) REFERENCES `parties` (`id`),
  CONSTRAINT `player_bestias_bestia_id` FOREIGN KEY (`bestia_id`) REFERENCES `bestias` (`id`),
  CONSTRAINT `player_bestias_master_id` FOREIGN KEY (`master_id`) REFERENCES `accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


DROP TABLE IF EXISTS `client_vars`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `client_vars` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `data` tinyblob DEFAULT NULL,
  `cvar_key` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `account_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `key_client_vars_id` (`cvar_key`),
  KEY `fk_client_vars_account_id` (`account_id`),
  CONSTRAINT `client_vars_account_id` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `component_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `component_data` (
  `id` bigint(20) NOT NULL,
  `data` blob NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `craft_recipe`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `craft_recipe` (
  `id` bigint(20) NOT NULL,
  `recipe_data` text COLLATE utf8_bin NOT NULL,
  `item_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `craft_recipe_item_id` (`item_id`),
  CONSTRAINT `FK_craft_recipe_item_id` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `entity_positions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `entity_positions` (
  `entity_id` BIGINT(20) NOT NULL,
  `x` BIGINT(20) NOT NULL,
  `y` BIGINT(20) NOT NULL,
  `z` BIGINT(20) NOT NULL,
  PRIMARY KEY (`entity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `entity_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `entity_data` (
  `id` bigint(20) NOT NULL,
  `data` blob DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `items` (
  `id` BIGINT(20) NOT NULL,
  `item_db_name` VARCHAR(100) NOT NULL,
  `mesh` VARCHAR(100) NOT NULL,
  `price` INT(11) NOT NULL DEFAULT 0,
  `weight` INT(11) NOT NULL DEFAULT 0,
  `type` VARCHAR(100) NOT NULL,
  `level` INT(3) NOT NULL DEFAULT 1,
  `usable_default_range` INT(11) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `map_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `map_data` (
  `height` bigint(20) NOT NULL,
  `width` bigint(20) NOT NULL,
  `x` bigint(20) NOT NULL,
  `y` bigint(20) NOT NULL,
  `data` longblob NOT NULL,
  PRIMARY KEY (`height`,`width`,`x`,`y`),
  KEY `x_idx` (`x`),
  KEY `y_idx` (`y`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `map_parameters`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `map_parameters` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `create_date` datetime DEFAULT NULL,
  `min_settlement_distance` int(11) NOT NULL,
  `name` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `population` bigint(20) NOT NULL,
  `seed` int(11) NOT NULL,
  `settlement_count` int(11) NOT NULL,
  `water_land_ratio` float NOT NULL,
  `depth` bigint(20) NOT NULL,
  `width` bigint(20) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `parties`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `parties` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8_bin NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_parties_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `player_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `player_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `amount` int(11) NOT NULL,
  `account_id` bigint(20) NOT NULL,
  `item_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_player_items_item_account` (`item_id`,`account_id`),
  KEY `player_items_account_id` (`account_id`),
  CONSTRAINT `FK_player_items_accounts_id` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`),
  CONSTRAINT `FK_player_items_items_id` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `script_vars`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `script_vars` (
  `id` bigint(20) NOT NULL,
  `data` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `script_key` varchar(255) COLLATE utf8_bin NOT NULL,
  PRIMARY KEY (`id`),
  KEY `name_id_key` (`script_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `status_effect`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `status_effect` (
  `id` bigint(20) NOT NULL,
  `armor_mult_mod` float NOT NULL,
  `armor_sum_mod` int(11) NOT NULL,
  `atk_mult_mod` float NOT NULL,
  `atk_sum_mod` int(11) NOT NULL,
  `database_name` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `def_mult_mod` float NOT NULL,
  `def_sum_mod` int(11) NOT NULL,
  `sp_armor_mult_mod` float NOT NULL,
  `sp_armor_sum_mod` int(11) NOT NULL,
  `sp_atk_mult_mod` float NOT NULL,
  `sp_atk_sum_mod` int(11) NOT NULL,
  `sp_def_mult_mod` float NOT NULL,
  `sp_def_sum_mod` int(11) NOT NULL,
  `spd_mult_mod` float NOT NULL,
  `spd_sum_mod` int(11) NOT NULL,
  `is_client_visible` bit(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `drop_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `drop_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `probability` int(11) NOT NULL,
  `bestia_id` bigint(20) NOT NULL,
  `item_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `drop_items_bestia_id` (`bestia_id`),
  KEY `drop_items_item_id` (`item_id`),
  CONSTRAINT `FK_drop_items_item_id_item_id` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`),
  CONSTRAINT `FK_drop_items_bestia_id_bestias_id` FOREIGN KEY (`bestia_id`) REFERENCES `bestias` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `guilds`;
CREATE TABLE `guilds` (
  `id` BIGINT(20) NOT NULL,
  `name` VARCHAR(45) NOT NULL,
  `leader_id` BIGINT(20) NOT NULL,
  `level` INT(11) NOT NULL DEFAULT 1,
  `exp` INT(11) NOT NULL DEFAULT 0,
  `last_leader_change` TIMESTAMP NULL,
  `created` TIMESTAMP NOT NULL DEFAULT NOW(),
  PRIMARY KEY (`id`),
  KEY `guilds_leader_guild_member_id` (`leader_id`),
  CONSTRAINT `FK_guilds_leader_guild_member_id`
    FOREIGN KEY (`leader_id`)
    REFERENCES `guild_member` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;


DROP TABLE IF EXISTS `guild_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `guild_member` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `exp_earned` int(11) NOT NULL,
  `guild_id` bigint(20) NOT NULL,
  `member_id` bigint(20) NOT NULL,
  `rank_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `guild_member_guild_id_player_bestia_id` (`guild_id`,`member_id`),
  KEY `guild_member_player_bestia_id` (`member_id`),
  KEY `guild_member_rank_id` (`rank_id`),
  CONSTRAINT `FK_guild_member_guild_ranks_id` FOREIGN KEY (`rank_id`) REFERENCES `guild_ranks` (`id`),
  CONSTRAINT `FK_guild_member_guilds_id` FOREIGN KEY (`guild_id`) REFERENCES `guilds` (`id`),
  CONSTRAINT `FK_guild_member_player_bestias_id` FOREIGN KEY (`member_id`) REFERENCES `player_bestias` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;


DROP TABLE IF EXISTS `guild_ranks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `guild_ranks` (
  `id` BIGINT(20) NOT NULL,
  `guild_id` BIGINT(20) NOT NULL,
  `name` VARCHAR(45) NOT NULL,
  `can_edit_member` BIT(1) NOT NULL,
  `can_edit_ranks` BIT(1) NOT NULL,
  `tax_rate` FLOAT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `guild_ranks_guild_id` (`guild_id`),
  CONSTRAINT `FK_guild_ranks_guild_id` FOREIGN KEY (`guild_id`) REFERENCES `guilds` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

SET FOREIGN_KEY_CHECKS=1;

INSERT INTO `accounts` (`id`, `additional_bestia_slots`, `username`, `is_activated`, `account_type`, `gender`, `hairstyle`)
VALUES ('1', '0', 'rocket', 1, 'ADMIN', 'MALE', 'MALE_01');

INSERT INTO `bestias` (`id`, `bestia_db_name`, `default_name`, `element`, `exp_gained`, `is_boss`, `level`, `mesh`, `type`, `temperature_kind`, `b_agi`, `b_dex`, `b_hp`, `b_stamina`, `b_int`, `b_mana`, `b_str`, `b_vit`, `b_will`)
VALUES ('1', 'doom_master', 'Doom Master', 'NORMAL', '10000', true, '100', 'doom_master', 'DEMI_HUMAN', 'MEDIUM', '10', '10', '10', '10', '10', '10', '10', '10', '10');

INSERT INTO `player_bestias` (`id`, `current_health`, `current_mana`, `current_stamina`, `max_health`, `max_mana`, `max_stamina`, `x`, `y`, `z`, `exp`, `gender`, `level`, `save_x`, `save_y`, `save_z`, `master_id`, `bestia_id`, `account_id`)
VALUES ('1', '100', '100', '100', '100', '100', '100', '0', '0', '0', '10', 'MALE', '1', '0', '0', '0', '1', '1', '1');

