/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `accounts`
--

DROP TABLE IF EXISTS `accounts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `accounts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `additional_bestia_slots` int(11) NOT NULL,
  `banned_until_date` date DEFAULT NULL,
  `email` varchar(64) COLLATE utf8_bin NOT NULL,
  `username` varchar(32) COLLATE utf8_bin NOT NULL,
  `hairstyle` int(11) DEFAULT NULL,
  `is_activated` bit(1) NOT NULL,
  `language` varchar(5) COLLATE utf8_bin NOT NULL,
  `login_token` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `password` varchar(255) COLLATE utf8_bin NOT NULL,
  `register_date` date DEFAULT NULL,
  `remarks` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `user_level` varchar(20) COLLATE utf8_bin DEFAULT NULL,
  `party_id` bigint(20) DEFAULT NULL,
  `gender` varchar(6) COLLATE utf8_bin DEFAULT NULL,
  `banned_until` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_email` (`email`),
  UNIQUE KEY `UK_username` (`username`),
  KEY `FK_account_party_id` (`party_id`),
  CONSTRAINT `FK_account_party_id` FOREIGN KEY (`party_id`) REFERENCES `parties` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `attacks`
--

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
  `atk_range` int(11) NOT NULL,
  `strength` int(11) NOT NULL,
  `target` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `has_script` bit(1) NOT NULL,
  `type` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_attacks_attack_db_name` (`attack_db_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `bestia_attacks`
--

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

--
-- Table structure for table `bestias`
--

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
  `b_agi` int(11) DEFAULT NULL,
  `b_dex` int(11) DEFAULT NULL,
  `b_hp` int(11) DEFAULT NULL,
  `b_sta` int(11) DEFAULT NULL,
  `b_int` int(11) DEFAULT NULL,
  `b_mana` int(11) DEFAULT NULL,
  `b_str` int(11) DEFAULT NULL,
  `b_vit` int(11) DEFAULT NULL,
  `b_will` int(11) DEFAULT NULL
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_bestia_bestia_db_name` (`bestia_db_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `clientvars`
--

DROP TABLE IF EXISTS `clientvars`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `clientvars` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `data` tinyblob DEFAULT NULL,
  `cvar_key` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `account_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `key_clientvars_id` (`cvar_key`),
  KEY `fk_clientvars_account_id` (`account_id`),
  CONSTRAINT `clientvars_account_id` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `component_data`
--

DROP TABLE IF EXISTS `component_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `component_data` (
  `id` bigint(20) NOT NULL,
  `data` blob NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `craft_recipe`
--

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


--
-- Table structure for table `drop_items`
--

DROP TABLE IF EXISTS `drop_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `drop_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `probability` int(11) NOT NULL,
  `bestia_id` bigint(20) NOT NULL,
  `item_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqad62h3v1rfvk4jnhcwh5ra0y` (`bestia_id`),
  KEY `FK7u9b7ryur8bmbw1tw40m8my9k` (`item_id`),
  CONSTRAINT `FK_drop_items_item_id_item_id` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`),
  CONSTRAINT `FK_drop_items_bestia_id_bestias_id` FOREIGN KEY (`bestia_id`) REFERENCES `bestias` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `entity_data`
--

DROP TABLE IF EXISTS `entity_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `entity_data` (
  `id` bigint(20) NOT NULL,
  `data` blob DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `guild_member`
--

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

--
-- Table structure for table `login_info`
--

DROP TABLE IF EXISTS `login_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `login_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `account` tinyblob DEFAULT NULL,
  `browser_agent` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `event_date` datetime DEFAULT NULL,
  `event_type` int(11) DEFAULT NULL,
  `ip` varchar(255) COLLATE utf8_bin DEFAULT NULL,
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

--
-- Table structure for table `map_data`
--

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

--
-- Table structure for table `map_parameters`
--

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

--
-- Table structure for table `parties`
--

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

--
-- Table structure for table `player_items`
--

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

--
-- Table structure for table `script_vars`
--

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

--
-- Table structure for table `status_effect`
--

DROP TABLE IF EXISTS `status_effect`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `status_effect` (
  `id` bigint(20) NOT NULL,
  `armor_mult_mod` float NOT NULL,
  `armor_sum_mod` int(11) NOT NULL,
  `atk_mult_mod` float NOT NULL,
  `atk_sum_mod` int(11) NOT NULL,
  `datebase_name` varchar(255) COLLATE utf8_bin DEFAULT NULL,
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

