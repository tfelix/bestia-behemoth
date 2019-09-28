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
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_email` (`email`),
  UNIQUE KEY `UK_username` (`username` ASC),
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
  `id` int(11) NOT NULL,
  `animation` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `based_status` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `casttime` int(11) NOT NULL,
  `cooldown` int(11) NOT NULL,
  `attack_db_name` varchar(255) COLLATE utf8_bin NOT NULL,
  `element` varchar(255) COLLATE utf8_bin NOT NULL,
  `indicator` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `line_of_sight` bit(1) NOT NULL,
  `mana_cost` int(11) NOT NULL,
  `atk_range` int(11) NOT NULL,
  `strength` int(11) NOT NULL,
  `target` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `has_script` bit(1) NOT NULL,
  `type` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_tigj3iera7lvpgs98piy9b9g9` (`attack_db_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `bestia_attacks`
--

DROP TABLE IF EXISTS `bestia_attacks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `bestia_attacks` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `min_level` int(11) NOT NULL,
  `attack_id` int(11) NOT NULL,
  `bestia_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKrul2cv5tx3hk5r0f0fjc0coy1` (`attack_id`,`bestia_id`),
  KEY `FKhkh96eh7359axliwg1kw0efoh` (`bestia_id`),
  CONSTRAINT `FKhkh96eh7359axliwg1kw0efoh` FOREIGN KEY (`bestia_id`) REFERENCES `bestias` (`id`),
  CONSTRAINT `FKp7ae9tdi1jbrldgp6ibsgooaa` FOREIGN KEY (`attack_id`) REFERENCES `attacks` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `bestias`
--

DROP TABLE IF EXISTS `bestias`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `bestias` (
  `id` int(11) NOT NULL,
  `bestia_db_name` varchar(100) COLLATE utf8_bin NOT NULL,
  `default_name` varchar(100) COLLATE utf8_bin NOT NULL,
  `element` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `exp_gained` int(11) NOT NULL,
  `image` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `is_boss` bit(1) NOT NULL,
  `level` int(11) NOT NULL,
  `script_exec` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `sprite` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `visual` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `type` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `b_agi` int(11) DEFAULT NULL,
  `b_dex` int(11) DEFAULT NULL,
  `b_hp` int(11) DEFAULT NULL,
  `b_int` int(11) DEFAULT NULL,
  `b_mana` int(11) DEFAULT NULL,
  `b_str` int(11) DEFAULT NULL,
  `b_vit` int(11) DEFAULT NULL,
  `b_will` int(11) DEFAULT NULL,
  `visual_type` varchar(255) COLLATE utf8_bin DEFAULT NULL,
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
  `data` tinyblob,
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
  `id` int(11) NOT NULL,
  `data` blob NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `drop_items`
--

DROP TABLE IF EXISTS `drop_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `drop_items` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `probability` int(11) NOT NULL,
  `bestia_id` int(11) NOT NULL,
  `item_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqad62h3v1rfvk4jnhcwh5ra0y` (`bestia_id`),
  KEY `FK7u9b7ryur8bmbw1tw40m8my9k` (`item_id`),
  CONSTRAINT `FK7u9b7ryur8bmbw1tw40m8my9k` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`),
  CONSTRAINT `FKqad62h3v1rfvk4jnhcwh5ra0y` FOREIGN KEY (`bestia_id`) REFERENCES `bestias` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `entity_data`
--

DROP TABLE IF EXISTS `entity_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `entity_data` (
  `id` int(11) NOT NULL,
  `data` blob,
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
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `exp_earned` int(11) NOT NULL,
  `guild_id` int(11) NOT NULL,
  `player_bestia_id` bigint(20) NOT NULL,
  `rank_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKs16um83tuhndfb126wq4xh3u9` (`guild_id`,`player_bestia_id`),
  UNIQUE KEY `UK7crydqebi83k00g7jvt1xftej` (`guild_id`),
  KEY `hjchasjkd82hdcgjhdsfsafhgdb_idx` (`player_bestia_id`),
  KEY `FKpkn9yhdkbr1a65bwmqv3f6vx8` (`rank_id`),
  CONSTRAINT `FKb3172le74tvsn9sie3fnorgf7` FOREIGN KEY (`player_bestia_id`) REFERENCES `player_bestias` (`id`),
  CONSTRAINT `FKha9f2dmqo2d91tplhgmlpsixr` FOREIGN KEY (`guild_id`) REFERENCES `guilds` (`id`),
  CONSTRAINT `FKpkn9yhdkbr1a65bwmqv3f6vx8` FOREIGN KEY (`rank_id`) REFERENCES `guild_ranks` (`id`),
  CONSTRAINT `hjchasjkd82hdcgjhdsfsafhgdb` FOREIGN KEY (`player_bestia_id`) REFERENCES `player_bestias` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `guild_ranks`
--

DROP TABLE IF EXISTS `guild_ranks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `guild_ranks` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `can_edit_member` bit(1) NOT NULL,
  `can_edit_ranks` bit(1) NOT NULL,
  `name` varchar(255) COLLATE utf8_bin NOT NULL,
  `tax_rate` float NOT NULL,
  `guild_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK2whnvwrbalr6dyuo1gkxovxhy` (`guild_id`),
  CONSTRAINT `FK2whnvwrbalr6dyuo1gkxovxhy` FOREIGN KEY (`guild_id`) REFERENCES `guilds` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `guilds`
--

DROP TABLE IF EXISTS `guilds`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `guilds` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `creation_date` datetime DEFAULT NULL,
  `emblem` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `last_leader_change_date` datetime DEFAULT NULL,
  `level` int(11) NOT NULL,
  `name` varchar(40) COLLATE utf8_bin NOT NULL,
  `leader_id` int(11) NOT NULL,
  `experience` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `guilds_name` (`name`),
  UNIQUE KEY `guilds_leader_id` (`leader_id`),
  CONSTRAINT `fk_guilds_member_leader_id` FOREIGN KEY (`leader_id`) REFERENCES `guild_member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `i18ns`
--

DROP TABLE IF EXISTS `i18ns`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `i18ns` (
  `category` varchar(255) COLLATE utf8_bin NOT NULL,
  `translation_key` varchar(255) COLLATE utf8_bin NOT NULL,
  `lang` varchar(5) COLLATE utf8_bin NOT NULL,
  `value` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  PRIMARY KEY (`category`,`translation_key`,`lang`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `items`
--

DROP TABLE IF EXISTS `items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `items` (
  `id` int(11) NOT NULL,
  `image` varchar(255) COLLATE utf8_bin NOT NULL,
  `indicator` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `item_db_name` varchar(255) COLLATE utf8_bin NOT NULL,
  `price` int(11) NOT NULL,
  `type` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `usable_range` int(11) NOT NULL,
  `weight` int(11) NOT NULL,
  `usable_default_range` int(11) NOT NULL,
  `level` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_8gg9gkwuvj66l7r2jr3i90xyy` (`item_db_name`)
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
  `account` tinyblob,
  `browser_agent` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `event_date` datetime DEFAULT NULL,
  `event_type` int(11) DEFAULT NULL,
  `ip` varchar(255) COLLATE utf8_bin DEFAULT NULL,
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
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `create_date` datetime DEFAULT NULL,
  `min_settlement_distance` int(11) NOT NULL,
  `name` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `population` bigint(20) NOT NULL,
  `seed` int(11) NOT NULL,
  `settlement_count` int(11) NOT NULL,
  `water_land_ratio` float NOT NULL,
  `height` bigint(20) NOT NULL,
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
  UNIQUE KEY `UK_jytip6v7llnm2othdgk7ts8s8` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `player_bestias`
--

DROP TABLE IF EXISTS `player_bestias`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `player_bestias` (
  `id` bigint(20) NOT NULL,
  `current_mana` int(11) NOT NULL,
  `current_health` int(11) NOT NULL,
  `x` bigint(20) NOT NULL,
  `y` bigint(20) NOT NULL,
  `ev_agi` int(11) DEFAULT NULL,
  `ev_dex` int(11) DEFAULT NULL,
  `ev_hp` int(11) DEFAULT NULL,
  `ev_int` int(11) DEFAULT NULL,
  `ev_mana` int(11) DEFAULT NULL,
  `ev_str` int(11) DEFAULT NULL,
  `b_vit` int(11) DEFAULT NULL,
  `ev_will` int(11) DEFAULT NULL,
  `exp` int(11) NOT NULL,
  `iv_spd` int(11) DEFAULT NULL,
  `iv_dex` int(11) DEFAULT NULL,
  `iv_hp` int(11) DEFAULT NULL,
  `iv_sp_atk` int(11) DEFAULT NULL,
  `iv_mana` int(11) DEFAULT NULL,
  `iv_atk` int(11) DEFAULT NULL,
  `iv_def` int(11) DEFAULT NULL,
  `iv_sp_def` int(11) DEFAULT NULL,
  `level` int(11) NOT NULL,
  `name` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `savex` bigint(20) DEFAULT NULL,
  `savey` bigint(20) DEFAULT NULL,
  `master_id` bigint(20) DEFAULT NULL,
  `bestia_id` int(11) NOT NULL,
  `account_id` bigint(20) NOT NULL,
  `entity_id` bigint(20) NOT NULL,
  `max_health` int(11) NOT NULL,
  `max_mana` int(11) NOT NULL,
  `party` tinyblob,
  `party_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_8cesygf9vnunkwbaay3b79dso` (`master_id`),
  KEY `FKf2abbxo21c1dyko75xpf7tuk7` (`bestia_id`),
  KEY `FK2lj7purwyjg5mbgf0wh3v6ofg` (`account_id`),
  KEY `FK9xep2kx1hranow59va87eg2wj` (`party_id`),
  CONSTRAINT `FK2lj7purwyjg5mbgf0wh3v6ofg` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`),
  CONSTRAINT `FK9xep2kx1hranow59va87eg2wj` FOREIGN KEY (`party_id`) REFERENCES `parties` (`id`),
  CONSTRAINT `FKf2abbxo21c1dyko75xpf7tuk7` FOREIGN KEY (`bestia_id`) REFERENCES `bestias` (`id`),
  CONSTRAINT `FKsm6bf38aqgyu3ah6vtb7s5sh8` FOREIGN KEY (`master_id`) REFERENCES `accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `player_items`
--

DROP TABLE IF EXISTS `player_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `player_items` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `amount` int(11) NOT NULL,
  `account_id` bigint(20) NOT NULL,
  `item_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgt740lb897ocnvahsoq0byqvp` (`item_id`,`account_id`),
  KEY `FK76xgq2vqwj3m10yveml6gc3d3` (`account_id`),
  CONSTRAINT `FK76xgq2vqwj3m10yveml6gc3d3` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`),
  CONSTRAINT `FKtfqj8qn9nb0ivq6gi7fok9g9b` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`)
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
  UNIQUE KEY `UKnbn83k3lyy9a16niqpvy8e6we` (`id`),
  UNIQUE KEY `UKgvlcqi30rg4gjg33qxu3bif0o` (`id`),
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
  `id` int(11) NOT NULL,
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

--
-- Table structure for table `tileset`
--

DROP TABLE IF EXISTS `tileset`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tileset` (
  `id` int(11) NOT NULL,
  `max_gid` bigint(20) DEFAULT NULL,
  `min_gid` bigint(20) DEFAULT NULL,
  `name` varchar(255) COLLATE utf8_bin NOT NULL,
  `data` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_9ga03sbm0a7fc7hoi1nru41l1` (`name`),
  KEY `IDXplpdcccyv7p45w98jp4o87wv` (`min_gid`),
  KEY `IDXckvnfggm3vtgiq9iche33ukrv` (`max_gid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2018-09-15 11:43:29
