CREATE DATABASE  IF NOT EXISTS `bestia` /*!40100 DEFAULT CHARACTER SET utf8 COLLATE utf8_bin */;
USE `bestia`;
-- MySQL dump 10.13  Distrib 5.6.17, for Win32 (x86)
--
-- Host: 127.0.0.1    Database: bestia
-- ------------------------------------------------------
-- Server version	5.5.5-10.1.16-MariaDB

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
  `gold` int(11) NOT NULL,
  `hairstyle` int(11) DEFAULT NULL,
  `is_activated` bit(1) NOT NULL,
  `language` varchar(255) COLLATE utf8_bin NOT NULL,
  `last_login` date DEFAULT NULL,
  `login_token` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `password` varchar(255) COLLATE utf8_bin NOT NULL,
  `register_date` date DEFAULT NULL,
  `remarks` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `user_level` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `party_id` bigint(20) DEFAULT NULL,
  `gender` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_n7ihswpy07ci568w34q0oi8he` (`email`),
  KEY `FKelu57umktnd37purw97ke85jp` (`party_id`),
  CONSTRAINT `FKelu57umktnd37purw97ke85jp` FOREIGN KEY (`party_id`) REFERENCES `parties` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `accounts`
--

LOCK TABLES `accounts` WRITE;
/*!40000 ALTER TABLE `accounts` DISABLE KEYS */;
INSERT INTO `accounts` VALUES (1,0,NULL,'thomas.felix@tfelix.de',1000,0,'','De-de',NULL,'489fb2fd-ef07-462c-b352-003e85e4d5f5','lPlDi+l7PcN9WapRXsh5AGYJRAmaYKT9tjcBBp+XzF+4TteZ+Aj5lxX9tsirg2iy7HqzROXuH8ekczGscDSfppeyXRWBttUm3R6vqW6dNSkb0sYnTYtXWFQzyH8oT0ss5fAVbUsnZxyBu1Rby67AVEFjYE/YP8X3BtLSSREJbOo=$xdiW227IAxcEgK1zr/HfdtnBVjxmGJxiMYrWujnqZtc=',NULL,NULL,'ADMIN',NULL,NULL),(2,0,NULL,'test2@tfelix.de',1000,0,'','De-de',NULL,'04473c9f-65e9-4f59-9075-6da257a21826','',NULL,NULL,'USER',NULL,NULL);
/*!40000 ALTER TABLE `accounts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `akka_journal`
--

DROP TABLE IF EXISTS `akka_journal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `akka_journal` (
  `persistence_key` bigint(20) NOT NULL,
  `sequence_nr` bigint(20) NOT NULL,
  `message` blob NOT NULL,
  PRIMARY KEY (`persistence_key`,`sequence_nr`),
  CONSTRAINT `akka_journal_ibfk_1` FOREIGN KEY (`persistence_key`) REFERENCES `akka_metadata` (`persistence_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `akka_journal`
--

LOCK TABLES `akka_journal` WRITE;
/*!40000 ALTER TABLE `akka_journal` DISABLE KEYS */;
/*!40000 ALTER TABLE `akka_journal` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `akka_metadata`
--

DROP TABLE IF EXISTS `akka_metadata`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `akka_metadata` (
  `persistence_key` bigint(20) NOT NULL AUTO_INCREMENT,
  `persistence_id` varchar(255) COLLATE utf8_bin NOT NULL,
  `sequence_nr` bigint(20) NOT NULL,
  PRIMARY KEY (`persistence_key`),
  UNIQUE KEY `persistence_id` (`persistence_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `akka_metadata`
--

LOCK TABLES `akka_metadata` WRITE;
/*!40000 ALTER TABLE `akka_metadata` DISABLE KEYS */;
/*!40000 ALTER TABLE `akka_metadata` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `akka_snapshot`
--

DROP TABLE IF EXISTS `akka_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `akka_snapshot` (
  `persistence_key` bigint(20) NOT NULL,
  `sequence_nr` bigint(20) NOT NULL,
  `created_at` bigint(20) NOT NULL,
  `snapshot` blob NOT NULL,
  PRIMARY KEY (`persistence_key`,`sequence_nr`),
  CONSTRAINT `akka_snapshot_ibfk_1` FOREIGN KEY (`persistence_key`) REFERENCES `akka_metadata` (`persistence_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `akka_snapshot`
--

LOCK TABLES `akka_snapshot` WRITE;
/*!40000 ALTER TABLE `akka_snapshot` DISABLE KEYS */;
/*!40000 ALTER TABLE `akka_snapshot` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `attacks`
--

LOCK TABLES `attacks` WRITE;
/*!40000 ALTER TABLE `attacks` DISABLE KEYS */;
INSERT INTO `attacks` VALUES (1,'tackle','NORMAL',300,0,'tackle','NORMAL','null','',5,1,10,'ENEMY_ENTITY','\0',NULL);
/*!40000 ALTER TABLE `attacks` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `bestia_attacks`
--

LOCK TABLES `bestia_attacks` WRITE;
/*!40000 ALTER TABLE `bestia_attacks` DISABLE KEYS */;
/*!40000 ALTER TABLE `bestia_attacks` ENABLE KEYS */;
UNLOCK TABLES;

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
  UNIQUE KEY `UK_egdca22syhdicxngo00qc3poy` (`bestia_db_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bestias`
--

LOCK TABLES `bestias` WRITE;
/*!40000 ALTER TABLE `bestias` DISABLE KEYS */;
INSERT INTO `bestias` VALUES (1,'blob','Poring','NORMAL',10,'poring','\0',1,NULL,'poring','PACK','FORMLESS',1,1,1,1,1,1,1,1,'PACK'),(2,'mastersmith','Master','NORMAL',1000,'master_1','\0',1,NULL,'mastersmith','DYNAMIC','HUMANOID',5,5,5,5,5,5,5,5,'DYNAMIC');
/*!40000 ALTER TABLE `bestias` ENABLE KEYS */;
UNLOCK TABLES;

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
  KEY `key_idx` (`cvar_key`),
  KEY `FKnc2khtlwq8ukd38dpbdjoh15l` (`account_id`),
  CONSTRAINT `FKnc2khtlwq8ukd38dpbdjoh15l` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `clientvars`
--

LOCK TABLES `clientvars` WRITE;
/*!40000 ALTER TABLE `clientvars` DISABLE KEYS */;
/*!40000 ALTER TABLE `clientvars` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `component_data`
--

LOCK TABLES `component_data` WRITE;
/*!40000 ALTER TABLE `component_data` DISABLE KEYS */;
/*!40000 ALTER TABLE `component_data` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `drop_items`
--

LOCK TABLES `drop_items` WRITE;
/*!40000 ALTER TABLE `drop_items` DISABLE KEYS */;
/*!40000 ALTER TABLE `drop_items` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `entity_data`
--

LOCK TABLES `entity_data` WRITE;
/*!40000 ALTER TABLE `entity_data` DISABLE KEYS */;
/*!40000 ALTER TABLE `entity_data` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `guild_member`
--

LOCK TABLES `guild_member` WRITE;
/*!40000 ALTER TABLE `guild_member` DISABLE KEYS */;
/*!40000 ALTER TABLE `guild_member` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `guild_ranks`
--

LOCK TABLES `guild_ranks` WRITE;
/*!40000 ALTER TABLE `guild_ranks` DISABLE KEYS */;
/*!40000 ALTER TABLE `guild_ranks` ENABLE KEYS */;
UNLOCK TABLES;

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
  UNIQUE KEY `UK_jdl3u9rp7vaqkdowxq64ddhso` (`name`),
  UNIQUE KEY `UK_k8oes0600ofjbc52on8eoo0et` (`leader_id`),
  CONSTRAINT `FKb59a6y95glk77fwskwfun4nak` FOREIGN KEY (`leader_id`) REFERENCES `guild_member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `guilds`
--

LOCK TABLES `guilds` WRITE;
/*!40000 ALTER TABLE `guilds` DISABLE KEYS */;
/*!40000 ALTER TABLE `guilds` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `i18ns`
--

LOCK TABLES `i18ns` WRITE;
/*!40000 ALTER TABLE `i18ns` DISABLE KEYS */;
/*!40000 ALTER TABLE `i18ns` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `items`
--

LOCK TABLES `items` WRITE;
/*!40000 ALTER TABLE `items` DISABLE KEYS */;
INSERT INTO `items` VALUES (1,'apple',NULL,'apple',1,'USABLE',0,1,0,0);
/*!40000 ALTER TABLE `items` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `login_info`
--

LOCK TABLES `login_info` WRITE;
/*!40000 ALTER TABLE `login_info` DISABLE KEYS */;
/*!40000 ALTER TABLE `login_info` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `map_data`
--

LOCK TABLES `map_data` WRITE;
/*!40000 ALTER TABLE `map_data` DISABLE KEYS */;
INSERT INTO `map_data` VALUES (100,100,0,0,'xÚíÏ¿nÔ@Çñ9ßå\n\"¤HÔĞ@gK´W!¥IäÒ¥as^ù6ò?Ös›‰Ç HIGEÉĞç1xÖg$òß‘>^YšÙßì—Ÿ²×z9¬¬ÆW¶Ugâ²Îl—¦‰ÏLslÔ_¼’±&‘L/e?÷õºÊRÓ[¯]¤2/†ŸVå ½6“¬ÕIêZ]¤2óv©*GiÈHÆŒd›‘ä¶.­ú>9‹®Yûá¶³ooŸm~|ı‰tÈóƒ¼\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Ôzytm6&^«+â—Ş›>u­vïúnn§29‘YëŞÛ®	ír3¾]˜zRY¯l«ÎÄeÙ\"Îm]Zõ}|n—*cM\"™¦27ÕrU{•§i˜JÆ©d;•ì¦’×µ«tškïrW½“ò ³Uî|:]v:üÿNÛÛÿ,Ê¤úñY¡^ìÎvH~(ÿÔı/VøëòùÊº|¥§²wã2]ıNÉvç/YÄF¶'),(100,100,0,100,'xÚíÏ¿nÔ@Çñ9ßå\n\"¤HÔĞ@gK´W!¥IäèÒds^ù6ò?Ös›‰—@JAIGEÉĞóHyÖgJAà;ÒÇ+K3û›ız#;­—ıÊj|a[u&.ëÌqišøÄ4‡FÍáÛW2Ö$’é™ìæ¾^WYjzëU¢³£TæÅğÓªì¥—fc’µº\"I]«‹TfŞ.Uå \rÉ˜‘l3’ÜÖ¥Uß\'§¡cÑ5k?ÜvòıüÙæ×·/‘H×ˆ<ßÁ»\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0F­—G—fcâµº\"~é½éS×j÷ñçãëæóT&G2kİ{Û5¡]®fÃ·SO*«ñ…mÕ™¸¬3[Ä¹­K«¾OíRe¬I$ÓTæ¦Z®j¯ò4\rSÉ8•l§’Û©äuí*]„æÚ»ÜUïäƒ<HÇl•ƒ{ß„E×„öÿ¿ÓööKEÇ2é‚~|V¨ÎOíüPîVvÿ‹‡î\\>_Y—¯ôXv®\\¦«¿)Ùíù¡¤G~'),(100,100,0,200,'xÚíÏ¿nÔ@Çñ9ßå\n\"¤HÔĞ@gK´W!¥Iä”Ğ¥as^ù6ò?Ös›‰Ç  ¤£¢Ì@Íc ñT¬Ï”‚<Áw¤W–fö7ûù§ì´^ö+«ñ…mÕ™¸¬3[Ä¥iâÓ\Z5‡¯^ÈX“H¦ç²›ûz]e©é­W‰ÎR™ÃO«²—^šIÖêŠ$u­.R™y»T•ƒ4d$cF²ÍHr[—V}Ÿœ†E×¬ıpÛÉõë\'›_>E\"]#òt/ï\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0µ^\\š‰×êŠø¹÷¦O]«İûï?|5§29’YëŞÚ®	ír5¾]˜zTY/l«ÎÄeÙ\"Îm]Zõ}|j—*cM\"™¦27ÕrU{•Çi˜JÆ©d;•ÜL%/kWé\"4×Şå®z#ïä^:f«Ü1x:]vÚÿÿNÛÛÿ-Ë¤úñY¡ı9µCò}¹]ßî~ñ°Â­Ëç+ëò•ËÎ•Ëtõ7%»9é„HF'),(100,100,0,300,'xÚíÏ¿nÔ@Çñ±ïrR$jHh A¶D{RšD@	]$Äæ¼òmä¬ç.>\n$ƒ‚’*%/@Ïc ñ¬Ï”‚<Áw¤-Ë3û›ıúKv:/ûµÕäÂvêLR5¹-“Ê´É‰išÃ×/e¬(–É¹ì¾YÕyf6Ö«ÄçG™ÌÊá£SÙË.ÍÚ¤+ueš¹Nç™L½]¨ÊA2Ò1#İf¤…m*«~“†yß®üpÚÉ··OÖ?¯¿Ä\"}+òt/ï\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0u^\\šµIVêÊä…÷f“¹Nû?~ún>O$:’içŞÛ¾\rír5}˜zT[M.l§Î$U“Û2)lSYõ›äÔ.TÆŠb™d23õbÙx•ÇY˜JÇ©t;•ŞL¥¯\ZWë<47Ş®~\'ä^6f«Ü1x:æ}vÚÿÿNÛÓÿ-KÔ›ñZ¡ÿù÷¦’ïË­Šİ}ãa…[‡Ï–ÖK=–+—ëòoJ~óş\rzG'),(100,100,0,400,'xÚíÏ¿nÔ@Çñ±ïrR$jHh ³%Ú«Ò$rJèÒ°9¯î6ò?Ös›‰7 ¥ ¤£JIICÏc ñ¬Ï”‚<Áw¤-Ë3û›ıòKvZ/û•ÕäÂ¶êLRÖ¹-’Ò4É‰išÃW/d¬(–É¹ì.}½®òÌôÖ«ÄçG™ÌŠá£UÙË.ÍÆ¤kuEš¹Vç™L½]¨ÊA2Ò1#İf¤K[—V}Ÿ†y×¬ıpÚÉ××O6?¯?Ç\"]#òt/ï\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0µ^\\šIÖêŠä¹÷¦Ï\\«İû?~7Ÿ&É´uom×„v¹šÏ.L=ª¬&¶Ug’²Îm‘,m]Zõ}rj*cE±L2™™j±ª½Êã,L¥ãTºJo¦Ò—µ«tškï–®z#ïä^6f«Ü1x:æ]vÚÿÿNÛÓÿ-KÔıx­PÏşüûÖÉ÷åVEî¾ñ°Â­Ãg+ë–+=–+—ëêoJ~óş\rÁşGØ'),(100,100,0,500,'xÚíÏ¿nÔ@Çñ±ïrR$jHhBgK´W!¥IäèÒds^ù6ò?ÖsA‘’Š’ ç1èò\0¬Ï\n(y‚ïH¯,ÍìoöëoÙé¼ì×V“KÛ©3IÕä¶L*Ó&§¦=2jŞ½–±¢X&ç²[øfUç™ÙX¯Ÿg2+‡ŸNe/»2k“®Ô•iæ:g2õv¡*YÈHÇŒt›‘¶©¬úMz:æ}»òÃm§ß/×¿¾}‰EúVäÅ^Ş\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00ê¼<¹2k“¬Ô•É+ïÍ&söŸ~>ıüÃÜL$:–iç>Ø¾\rír=¾}˜zV[M.m§Î$U“Û2)lSYõ›äÌ.TÆŠb™d23õbÙx•çY˜JÇ©t;•ŞM¥o\ZWë<47Ş®~/åQ6f«<0ø6tÌû6ì´ÿÿ¶·ÿ[*>‘¨6ã³B½ø°’Ë½Šn~ñ°Â½ËgKëŠ¥ÈÎµËuù7%¿;ÿ\0£FG¡'),(100,100,0,600,'xÚíÏ¿nÔ@Çñ9ßå\n\"¤HÔĞ@gK´W!¥Iä”P ¥as^ù6ò?Ös›‰6o@A™Š’ ç1xÖg”‚<Áw¤W–fö7ûõ·ì´^ö+«ñ…mÕ™¸¬3[Ä¥iâÓ\Z5‡o^ÉX“H¦ç²›ûz]e©é­W‰ÎR™ÃO«²—^šIÖêŠ$u­.R™y»T•ƒ4d$cF²ÍHr[—V}Ÿœ†E×¬ıpÛÉ÷wÏ6¿¾İD\"]#ò|/ï\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0µ^]š‰×êŠø¥÷¦O]«İ§Ÿ?ÿ0_¦29’Yë>Ø®	ír5¾]˜zRY/l«ÎÄeÙ\"Îm]Zõ}|j—*cM\"™¦27ÕrU{•§i˜JÆ©d;•ÜN%¯kWé\"4×Şå®z/åA:f«Ü3x:]vÚÿÿNÛÛÿ-Ë¤úñY¡^ŒGtİÉåNEoïñ°ÂËç+ëò•ËÎ•Ëtõ7%»=ÿ\0šxGj'),(100,100,0,700,'xÚíÑ1oÓ@€á/NÚ\n©3´,°Ùk&¤.­\\¶.\\“Sâ*±ƒ}I$~CG6&FÄÎÎÏ@‚ß€« ”6M¥Oö{Ò+Ò¿³ŞO?e#Ïd\'±Î?±¹‹?NûväÍÄ?4“=ãÌŞ«HªÕò¤},[ƒ,&ıĞÌmæÄ;Şes´ø“;ÙOÍÌS‚0Î]7”Nf{ÎÉnXÎªÁrF0°éØºl•;ºÅdš-Şvøåõ“ÙÏ=‘b\"òt»¼\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0+ˆÜôĞŠzDôP…zDBMè¡=ôˆ„šĞCHè¡	=t¡‡‘ĞCzèB=\"¡‡&ôĞ#zhB]è¡ÇÿZĞƒMuô GSÑCë¶ =šæ&-èA¦¡‡7mAz4Å*-èA¦ ‡«¶ =š€zÜ¦=èQg·mAzÔ=ôXGzĞ£ÖÕ‚ô¨#zè±Îô G¬»=èQ\'ôĞã.ZĞƒuq\'=òLœš™ñ§.ùÏ³ÌÌÃ8wÅûï?|3çmiíK\'ßÚbRn—³Îâ·(O=J¬óOlîbãÓ¾ù›­Ëæş‘í9©VË“v(›&é\rÓÌÉã°<T§‚å©àâTğ\"×-7§Y<ˆ“7òNî…Õl\'»W|Yîè“òN;ÿ¾Óòí/åH«(Í«Ï*×³êáıÊ“ïË¥å}½ú‹W¸ôòÍ¡Cw gqß\rÿLé_<$A÷>'),(100,100,0,800,'xÚíÑ?oÓ@Çñ\'š\n©34,°Ùk&¤.­\\…n]¸Æ\'çªÄ6çKê0 ñ2Ù˜yİyH¼.±\nt Cò<—ü,}U²sÎïóå\'íU–sí¢K]9£¢i‘êI4UetªÊ#åÔÑù5W«MÚÏl1ËÓD-´uÔ¾8N¨7YşS9:H®Ô\\Å3g&qb*7H¨kõÈ9ê\'şŒ¸9#^gº˜jgñĞß1¨Ë™]~Ûé·7Ïæ?¾~nÕ%Ñóğ>úïÎÖ÷o1xÈiğ€GèÁCNë¶€<Bmğ€G¨ÁCN›²€<Bk“ğ€GhÁCN›¶€<B\nrâ°€<BˆËğ!xÈ‰Óğ·<à!9nxÈ²€<$Æm\0xHÛ\02-àÁ¿?<à!5îíá©qïxH{wxÈ·€‡¼¸·<$Ç½<v×ƒ{kxÀCjÜ;Ã’ãŞáYÀC^Ü[Ác·<¸÷…<¤Æ½-<à!9îmá¶<äÅ½,à!9îíà±İÜ{ÂRãŞğ÷–ğØ.xÈ‹{GxlŸ÷†ğ€‡Ô¸÷ƒ<$Ç½<¶×òâŞğ÷®ğßƒ{3xÀCjÜ{Á’ãŞ»cyqo0=¸w‚<¤Æ½<à!9îà±›ğ÷Ş°€‡ä¸7‡Ç_U–]©¹ŠfÎL¢—ÖªEb*WøşøãúÔ¡Ö1u+óN×¥¿®»Ë¿µêI®]t©+gT4-R=‰2]Lµ³‹h¨Gš«Õ¦NB=•Æ…uô4ñOÅÍSñê©øö©øUar7ğ7Öd&KïéAÒœí¨Ïƒ¯ıƒºôïtøïwZ}ûŸ—jŸP«ö-šŸå¯ÍGgX-O~Hw®Nÿş_¼|…;_Şk“İ	í]›ÔŸ’Ş~şsl¥Ä'),(100,100,0,900,'xÚíÑ?oÓ@‡ñ_ş4R%fhY`³%ÖLH]Z¹**l]¸&§ÄUbû’:HH¼	F6&Ş\0;/‰÷€« tõñœôÄŠtç;ßçÓOÛ)ÛÏ|ˆ.|RÍó±ŸEs·ˆNÜâĞwøâÔšÑéZïÜv\'E¾ÌÆ‰[û\"X÷ü(±Áló§¶—\\º•‹—!ÅIZ†abıÂB°ƒ¤Ş#nöˆ·{ÄŸÏ}(ÖñY=cX-–Åæm\'__>Zıøü±kV-ÌïÕïŠvúŸÖö½ã¡ïÑöàšµ}÷Xà¡\\Û÷‡®GÛw¡PÛxhz´}*µí€Êµí€Êa¡Zá¡Zá¡Zá¡Za¡Zá¡Zá¡Zá¡Za¡Za¡Zá¡Zá¡Zá¡Za¡Zá¡Zá¡Zá¡Za¡Za¡Zá¡Zá¡Zá¡Za¡Zá¡Zá¡Zá¡Za¡Za¡Zá¡Zá¡Zá¡Za¡Zá¡Zá¡Zá¡Za¡Za¡Zá¡Zá¡Zá¡Za¡ZáADÿDea÷.İÊEËÎ¢§EáÖIZ†êí÷ûï¿¹=ëY¿L_ûjQO·«şæ·ªW=È|ˆ.|RÍó±ŸEŸÏ}(ÖÑ™kF§k½Ä.Mó\"ØÃ¤^7«âíªøzUü,O³0¬\'çE:I³WöÆî$ÍŞÁnYø¼1¬õ™öÿ~¦íÛÿª{lªnİ|V=4Ş—r³ó]»1zïnÿâÍn¼|0õéd\Zmç*‡éï]Æ×Ï_Â¯Ì'),(100,100,100,0,'xÚíÏ¿nÔ@Çñ9ßå\n\"¤HÔĞ@gK´W!¥IäèÒ°9¯|ùë¹‹MÄK ¥ ¤£¢äèy$Şõù¢ Oğ]éã•¥™ùÍ~ù%{­—ÃÊj|i[u&.ëÌqišøÌ4ÇFÍñë2I$ÓÙÏ}½®²ÔôÖ«D\'©Ì‹á§U9H¯ÌÆ$kuE’ºV©Ì¼]ªÊQ\Z2’1#Ùf$¹­K«¾OÎCÅ¢kÖ~˜vöíÍ“ÍÏ¯Ÿ#‘®yz‚÷\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0ŒZ/®ÌÆÄkuEüÜ{Ó§®ÕîÃ‡7ßÍ§©LNdÖºw¶kB¹\\Ï†oºUVãKÛª3qYg¶ˆs[—V}ŸÛ¥Êx&‘LS™›j¹ª½Êã4t%cW²íJn»’—µ«tŠkïrW½•÷r/³Uîh|*]v:üÿNÛé—ŠNeÒıø¬p>îîgí|÷—ín¹ûÅÃ\nÿŸ¯¬ËWz*{×.ÓÕŸ”ÛyÙo§äG~'),(100,100,100,100,'xÚíÏ¿nÔ@Çñ9ßå\n\"¤HÔĞ@g?ÀUHi9º4lÎ+ßFşÇzîbS ñH”tT”yô<ïÀúÌQ\'ø®ôñÊÒÌüf¿ı’½ÖËae5¾´­:—uf‹¸4M|fšc£æøÍKÏ$’é…ìç¾^WYjzëU¢‹“TæÅğÓª¤Wfc’µº\"I]«‹TfŞ.Uå(\rÉ˜‘l3’ÜÖ¥Uß\'ç¡bÑ5k?L;»yûlóóû×H¤kD„à}\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0£ÖË£+³1ñZ]¿ğŞô©kµûøãñç[óe*“™µî½íšP.×³áÛ…®\'•ÕøÒ¶êL\\Ö™-âÜÖ¥UßÇçv©2I$ÓTæ¦Z®j¯ò4\r]ÉØ•l»’]Wòªv•.Bqí]îªwòA¤c¶ÊÑ=¯CÅ¢kÂN‡ÿßi;ıïRÑ©Lº ŸÎ§İİÉÿüe»ûş+ü3|¾²._é©ì]»LWw)wó~ïÄHF'),(100,100,100,200,'xÚíÏ¿nÔ@Çñ9ßå\n\"¤HÔĞ@g?ÀUHi9º4lÎ+ßFşÇzîbS ñH”tT”¼\0Ô</@Åú|Q\'ø®ôñÊÒÌüf?ı”½ÖËae5¾´­:—uf‹¸4M|fšc£æøÅSÏ$’é…ìç¾^WYjzëU¢‹“TæÅğÓª¤Wfc’µº\"I]«‹TfŞ.Uå(\rÉ˜‘l3’ÜÖ¥Uß\'ç¡bÑ5k?L;ûòòÑæÇç‘H×ˆ<>Áû\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0F­—{Wfcâµº\"~â½éS×j÷öûı÷_Í‡©LNdÖº×¶kB¹\\Ï†oºTVãKÛª3qYg¶ˆs[—V}ŸÛ¥Êx&‘LS™›j¹ª½ÊÃ4t%cW²íJnº’gµ«tŠkïrW½’7r\'³Uni|*]v:üÿNÛé—ŠNeÒıø¬pŞíî_í|w÷—íîo·¿xXáŸáó•uùJOeïÚeºú“r3/û\r7³I'),(100,100,100,300,'xÚíÏ?nÔ@Çñ·ŞÍDH‘¨!¡ÙØ\n)M\"G @	1Y¼ùã·›‰K QPÒQQrzÄ¯7 \nr‚ïH[¿7¿7_~É^ëå°²\Z_ÚV‰Ë:³E\\š&>3Í±Qsüò™ŒkÉôBös_¯«,5½õ*ÑÅI*óbøhUÒ+³1ÉZ]‘¤®ÕE*3o—ªr”†ŒdÌH¶InëÒªï“óP±èšµN;ûöúÑæç×Ï‘H×ˆ<>Áû\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0F­—{Wfcâµº\"~ê½éS×j÷şÇıßÍ§©LNdÖº·¶kB¹\\Ï†gºTVãKÛª3qYg¶ˆs[—V}ŸÛ¥Ê¸&‘LS™›j¹ª½ÊÃ4t%cW²íJnº’çµ«tŠkïrW½‘wr\'³Uni|*]f:üÿLÛÓÿÊ¤úñZa}Øı{ÕÉww»Ùn÷Éí7FøçğùÊº|¥§²wí2]ıIÉnŞ¿È>GØ'),(100,100,100,400,'xÚíÑ¿nÔ@Çñ¹?IA„‰\Z\Zèì¸\n)M\"GF.\r›óên£;Û¬ç.>\n$Ş€\n)%%%\r=Ä;°w¾€\"õJ|WúØ²½³3ÖïãÙi¼”V“Û¨3É¼*ì,™›:95õ‘Qsô<—nõú28—½‰¯e‘™•õ*ıóãLvgë‡Fe?»4K“.ÔÍÒÌ5:ÊdèíXU³Ğ#íz¤›éÄVs«~•…£¶^øõi§Ÿ_<Z~ÿô¡/ÒÖ\"÷Cã=\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0ü·òf\0yÄ(\'¨G\\È#¹GLÈ#¹GLÈ#.ä\\È#&åÑx¹wi–&Y¨›%O¼7«Ì5Ú¾ùvÿú«y?Ş±÷Ê¶uØ.WÃõµ\rUJ«É…mÔ™d^v–Ll5·êWÉ™«t«×—A&»¦O+¯ò0UiW•nªÒ›ªôiåJ…Í•wW¾”×r\'ëz«ŞRø,ìµu˜éàß3mNÿ=TÿDzm°ê~+¬wÛo_šuç»Û·ÅöíÛÛÿx=Â‡ïN­›LõDv®\\¡Ó_]Š›ûOµ4R0'),(100,100,100,500,'xÚíÑ½nÓP€á/éÏ@…T‰Z–²Ù	©K+WA…­ns”¸Jâ`Ÿ´7ÄĞ‘‰‘`ç2Ø¸\0œº1´ô\'Éùl¿–ŞX‰||N¾çóOYIÙ\ZëšÔF¡7ˆ;¦ï\rÂ‘·¶Cn¿jKq5š²t kİ$;A81‰•æÁN «ıé—ÔÊzp„şØF}?ˆRÛ\nd91GÖÊfïá{øç{ø]ŒM&ş~şD+“éÛö¾¾Ş:ùñåSS$‰<[Ï7^#\'µ¯Èõ¹ê\ZºÂCOmÁCSxè©-xh\n]á¡§ÿYàG]»‰xÔ5<ôtS<ğ¨cxèé6xàQ§nku\n=İÅ<êĞ]-ğÀ£á¡§ûXàG•»¯xT9<ô4<ğ¨b³²À*†‡fiU=ÍÚ<ªÒ<,ğÀ£*á¡§yYàGÙ›§x”=<ô4o<ğ(k‹°À²†‡eek‘xàQ¶ğĞÓ¢-ğÀ£,¹°À²„‡\\YàGÂCO.-ğÀCs®-ğÀCs®-ğĞe\Zsm€šsm€‡N<ÜÏ½xèËõLğÀC«úr=,ğp=s<ğĞšëyã‡æ\\ÏòXà¡/×3ÂÍ¹õóp=c<ğĞšëùâ‡æ\\Ï÷V¥‰<:OBol£¾÷<IÂI¥6{ÿıñÇoáÙ’4vd9Şšl”?.§ËÓÏ,_õdh¬whR…Ş î˜¾×5ñÀØdâí›#+ÅÕhÊR «áğ¨\'Vù*¿XåŸ¯ò/Wù/âhh[ùÃqu£áy\'‚bo+›×,|™?ÑÊFù™6®>ÓùÛÿª¹+,oRü­üúPÜš[étç‡¿v.VüºşOğÏËW{&êöì®¬œFÛû³Kçòş‚&à—'),(100,100,100,600,'xÚíÑ±nÓPÅñ/Nš\n©3´,°Ù	©K+WE…©·ñUr«Ä6×7©Ã€ÄÊTb`dcbäØy$Ş\'nAtıN’éß¨‘ëœß—_²SyÙÏmˆ/lœ‰§Ef\'ñÔ”ñ‰)M0‡/O¥}u\"éËîÈ³<KÍÂú ÑùQ*ıÉòŸ*È^ziæ&™7IRW…A*=o‡!ÈAÚœ‘´g$«3’‘-¦6øErÖ\\1¨Ë™_~ÛÉ·×Oæ?¿~DêRäé^sğîwº†ioFíğĞŞ•ô@N{Wz¬¿=ğÒŞô@N{?zl®‡ö–ô rÚ[Òc³,è—ö´ rÚ[Òc³<´÷£=PÓŞô@N{;zl¶=ğÒŞ•ô@N{[z¬·‡ö^ô jÚ[ÑƒÈioEí² ^Ú;Ócı<´7¢=PÓŞ‡ô@N{zl¯=ğÒŞœô@N{wzà{hoBz zhï¡öşô rÚûÓ‚ÈiĞ‚Èi;ĞÏC{¤´-èAäh=°¢VôÀŠXÑ+z`E¬è=°¢VôÀŠXÑ+z`E¬è-°¢VôÀŠXÑ+z`E¬è-°¢VôÀŠXÑ+Z`E¬è-°¢V´ÀŠXÑ+Z`E¬èTååÁ¥™›xÜ$~æ½Y¤®\nõû?~7ŸºÒ9’^åŞÚºl.—«ŞòoİÜõ(·!¾°Up&™Ä#[Lmğ‹øÌƒ´¯N$İTú&äqÚÜ•´w%«»’Û»’ç…ËÃ ¹¸ğnäò7òNî¥íÙAî¸ñEsÅ .›gÚÿÿ3­¾ıïCEÇÒ©›íÏj^×í[ô¡Z|ÿæÓìæÓWwÿâå#üóåı±u£q8–+—…ñŸS²Û÷ßğˆ'),(100,100,100,700,'xÚíÑ±nÓ@Àñ/Nš\n©3´,°Ù	©K+WE¥[®ñ)¹*±Íù’:H¼CG6&ÆŠÇ@‚gà· ºÀğ\rK¿X±îüõÿø]¶\Z/»¥\ré¹m‚3é¼*ì,›:=2õ¾	fÿôXº«—HÿL¶\'¾Z”EnVÖIÎrÎÖš ;ù…YšlÜ,Ë]F¹¼‡ {yœ‘u3²ÍŒlb«¹\r~•Ä£¶^øõÛ®_>Y~ûô!ik‘§;qğö?:ÆE]h¡=t¡‡.´Ğ…ºĞBzèB]h¡=t¡….ôĞ…ºĞBzèB]è¡=t¡….ôĞ…ºĞBzèB]è¡=t¡….ôĞ…ºĞCzèB]è¡-t¡‡.ôĞ…ºĞCz\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0@Ôxypa–&]7KŸyoV¹kBûöëÃ÷_ÌU_z2hÜkÛÖq¹\\Ö¿mÜõ¨´!=·Mp&W…¥[Ímğ«ôÄƒtW/‘~.CS§•ò8»²nW¶Ù•İîÊW®£¸¸ònâÊWòFîåİì {wl|WŒÚ:i÷ïgÚ¼ı÷¡’CéµÑªû¬x½ënÉf=ùşÍÓâæéç»¿x}„?^>œZ7™†CÙºtE˜şšRÜŞÔJO<'),(100,100,100,800,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔ¥Î>ÀVHi9\nZèÒ0Y¼ùã·›‰K QPÒQQrzÄ¯9Áw¤G½7¿7_~ÉAëå¨²\Z_ÚV‰Ë:³E\\š&>3Í±Qsüê\\Æ5‰dz!÷s_o«,5½õ*ÑÅI*óbøiUÓ+³3ÉV]‘¤®Õe*3o×ª²HCF2f$ûŒ$·uiÕ÷É*T,»fë‡ÛÎ¾½~ºûùõs$Ò5\"ÏCğ9\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0€Qëåá•Ù™x«®ˆŸ{oúÔµÚ½ÿñèãwói*“™µî­íšP.×³áÛ…®Ç•ÕøÒ¶êL\\Ö™-âÜÖ¥UßÇ+»V×$’i*sS­7µWy’†®dìJö]ÉmWò¢v•.Cqí]îª7òNî¥c¶ÊâÆ—¡bÙ5a¦£ÿÏ´¿ıïPÑ©Lº ŸÖ‡q›®Ú!ùÁÍivsº¸ûÅÃÿ\\>ßX—oôT®]¦›?)Ùíş¬ÿ¨š'),(100,100,100,900,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔĞ@g`+¤4‰º4LÖ#ïDşÇøíÆ¦@BâH”tT)RpzÄ¯9Áw¤G½7¿7_ÉNëe¯²\ZŸÛV‰Ë:³E\\š&>6ÍQsğêDÆ5‰dz&÷s_¯«,5½õ*ÑÙa*óbøiUvÓ³1ÉZ]‘¤®ÕE*3o—ª²Ÿ†ŒdÌH¶InëÒªï“ÓP±èšµn;şöúéæçÕ—H¤kDí†à\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0£ÖËÃ³1ñZ]?÷Şô©kµ{ÿãÑ§ïæóT&‡2kİ[Û5¡\\.gÃ·]+«ñ¹mÕ™¸¬3[Ä¹­K«¾OíRe\\“H¦©ÌMµ\\Õ^åI\Zº’±+Ùv%·]É‹ÚUºÅµw¹«ŞÈ;¹—Ù*ûw4¾‹®	3íı¦íí‡ŠdÒıø¬°>Ûôº’Üœf7§î~ñ0Â?—ÏWÖå+=’K—éêOJv»ÿôß©b'),(100,100,200,0,'xÚíÏ¿nÔ@Çñ9ßå\n\"¤HÔĞ@gK´W!¥IäèÒ°9¯|ùë¹‹MÄcPPÒQQòPóH¼\0ëóDAà»ÒgW+ÍìoöÓOÙk½VVãKÛª3qYg¶ˆKÓÄg¦96j_>“qM\"™^È~îëu•¥¦·^%º8Ie^—Vå ½2“¬ÕIêZ]¤2óv©*GiÈHÆŒd›‘ä¶.­ú>9‹®Yûáµ³/¯m~|ş‰tÈãƒ¼\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Ôz¹we6&^«+â§Ş›>u­vï¾ßÿÕ|˜ÊäDf­{c»&”ËõlØ»Ğõ ²\Z_ÚV‰Ë:³EœÛº´êûøÜ.UÆ5‰dšÊÜTËUíU¦¡+»’mWrÓ•<¯]¥‹P\\{—»êµ¼•;é˜­rtKã‹P±èš0ÓáÿgÚ¾şw¨èT&]Ğß\në×î|ÒÉww·o»Snÿñ0Â?ÏWÖå+=•½k—éêOJvsşöHF'),(100,100,200,100,'xÚíÏ?nÔ@Çñ·ŞÍDH‘¨!¡Î>ÀVHi9º4LÖ#ïDşÇøíÆ¦@âH”tT”\\\0jÄ¨¯7 \nr‚ïHŸôŞüŞ|ú){­—ÃÊj|i[u&.ëÌqišøÌ4ÇFÍñ‹§2®I$ÓÙÏ}½®²ÔôÖ«D\'©Ì‹áÒª¤Wfc’µº\"I]«‹TfŞ.Uå(\rÉ˜‘l3’ÜÖ¥Uß\'ç¡bÑ5k?¼vöåå£ÍÏ#‘®y|‚÷\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0ŒZ/÷®ÌÆÄkuEüÄ{Ó§®Õîí÷ûï¿šS™œÈ¬u¯m×„r¹\r{ºTVãKÛª3qYg¶ˆs[—V}ŸÛ¥Ê¸&‘LS™›j¹ª½ÊÃ4t%cW²íJnº’gµ«tŠkïrW½’7r\'³Uni|*]f:üÿLÛ×ÿÊ¤úñ[aıÚïÚ!ùîîömwf·ÿxáŸÇç+ëò•ÊŞµËtõ\'%»9=óI'),(100,100,200,200,'xÚíÑ½nÓP€á/?í@…T‰ZØìÈ„Ô¥•+£ÂÖ…Óä(q•ØÁ>I$.ƒ±#7\03—Ä\r0áÄÄ@éO‚¾Ú¯¥Ç–¥süë}ÿM6²Tvbë¼›¹Èx£¤g‡ŞÈŒ½C3Ş3Îì=¥¼\ZMiËV?M&q/03›:iï²9œ¿dN¶ƒS35şÄEC?ˆ2×	¤Ú®s²3ür†¿˜á÷m2².ùGÅŠN>¤ó¯~|ñhúõÃyS$‹<Ş.o\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0€J	œôĞ(¤‡*ôĞ…z„BMè¡G(ôĞ„ºĞCPè¡	=ô…šĞCzè\n=4¡‡¡ĞCzèB=şÕ‚ô¨«Ë´ =êŠz\\¶=èQGôĞã*-èA:¹jzĞ£Nè¡ÇuZĞƒupİô GĞC›´ =ªì¦-èA*£‡«hAzTÑªZĞƒUD=VÙ‚ô¨’U· =ª„z¬£=èQëjAzT=ôXgzĞã6[wzĞã6£‡ÿ£=”õÈR¹wj¦Æ›¸hè=IS3¢Ìåo¾ÜûÉ¼kIc_ÚYôÊæãb¹œµç÷¼Øõ ¶Î;±™‹Œ7Jzvèõm2².yG¶ë¤¼\ZMi²iâî I<Š]~¹Ë_ìò—»ü§I»N±8I£~¿”×r\'(g;Ù½`ã³bE\'gÚùû™_ÿ}¨æ4òÂ¬ü­âú¾|fóÉw¾}^>/şãùşøøæÀFı;³¨ç¿¦ô–Ïq2•'),(100,100,200,300,'xÚíÑ½nÓPÆñ7Í@…T‰ZX}™º´rUTØ*!N“£äT‰OR‡‰ËèÀÈÆÄÈ\rÀÌe qL8q\nb ly^\'O¤«¦vóü>şÜË~jCtaóàL4ÎúvÍ$:1“CÌá‹S©^¦´Îewà³iÚOÌÜú Íó£D:£Åy½äÒÌL<\rn\'.İDÚŞöBƒ¤<#®Îˆ—gÄ›mğóø¬¼¢[L¦~ñi\'Ÿ_=š}ÿô¡)RLDï•ï²ÿvºÆĞßµÑCWôĞÓ:-èA:µnzĞ£NÑCOzĞ£¡,èA:D=!-èAÍ¡-èAÍ¡-è¡Ë‚ôĞÚ€ôĞÚ€:-èßŸz-è¡/ô&ô ‡Vzè½=èŞœôĞ\ZzozĞCsè½éQzè½-è¡9ôNôØ>ôÆô ‡ÖĞûÒƒšCïKúZĞC_èÍhAÍ¡w£Çæ{ 7¥=´†Ş“ôĞzOzl=ô…ŞôĞzGzlzCzĞCkèıèAÍ¡÷£ÇæZĞC_èMiAÍ¡w¥Gı=Ğ›ÑƒZCïEzh½=¶Ç‚úBoLzz w¢=´†ŞˆôĞz#zl§=ô…Ş›ôĞzszÔÃ½=è¡Õ½	=èA¡·§=4‡ŞŸôĞÚ€:=Ğ;¨(÷rïÒÌL4\rn=õŞÌ—‡âİ·û×_Ìû–4¤»7¶˜”—ËU{ñ³(ïzÚ]Ø<8³¾E›mğóèÌö‚T¯FSZ‰tLÚf>ÈÃ¤¼+®îŠ—wÅ7wÅÏ2—†nyqæİÀ¥¯å­ÜIª³ƒÜrãóòŠn1)ŸiÿßÏ´üô?Õ<–FQ6¯¾Vùú¹úßË|qòİÕ»_Wï>¹ı/á¯ï­Ã±ì\\¹~ş>¥óûµ _'),(100,100,200,400,'xÚíÑ¿nÓP†ñ/š\n©3´,°Ù	©K+WE…­§ÉQâ*±ƒ}’:HÜ+CG6&FF˜¹$n€	\'NAtÍ+ôXú%Šs¿c=~ØNYØ~æCtáËºhšı$šºYtâf‡.¸Ãç§Ö\\­¶uÎmwTäól˜¸¥/‚µÏëMV?Ê`{É¥[¸xÒIœ¤eè\'Ö-ü ;Hêq3#^ÏˆG>ŸúP,ã³zE¿šÍ‹ÕÓN>½x´øşñ}Û¬š™=Ş«ïŠ8ÅÚ¶;Ğ‚Ê¶İ‚Z=¶ışjè¡…Zè¡…Zh¡…Zh¡…Zè¡…Zè¡…Zh¡…Zh¡…Zè¡…Zè¡…Zè¡…Zh¡…Zh¡…Zè¡…Zè¡…Zè¡…Zh¡…Zh¡…Zè¡…Zè¡…Zè¡…Zh¡…Zh¡…Zè¡…Zè¡…Zh¡…Zh¡…Zè¡…Zè¡…Zè¡…Zh¡…Zh¡…Zè¡…Zè¡…Zè¡…Zh¡…Zh¡…Zè¡…Zè¡…Zh¡…\0\0\0\0\0\0\0\0à¿RvïÒ-\\4é$zRn™¤e¨Ş|»ÿî‹»îXëÈºeúÊW³z¹]uWŸU½ëAæCtáËºhšı$\Zù|êC±ŒÎü XsµÚÖI¬ç²Á8/‚=Lê]q³+^ïŠovÅOó4ızq^¤£4{i¯íNÒÌvpËÆgõŠ~5«Ï´ÿï3­ŸşçPíckUµeóZõõsóßçr5ùîæî×Íİ··¿ñê=¼7öéhmç*†ñï)Ã›ï_*z$ƒ'),(100,100,200,500,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHhBg?ÀUHi9\n\ntiØœW¾üõÜÅ¦@â1((ÓQQòPóHtTT¬Ï	ˆ‚<Áw¥WkÍìoöãÙi½ìUVãÛª3qYg¶ˆKÓÄ\'¦94j_Ê¸&‘LÏå~îëu•¥¦·^%:?Je^‡Ve7½4“¬ÕIêZ]¤2óv©*ûiÈHÆŒd›‘ä¶.­ú>9‹®Yûá¶“Ï¯6ß?]G\"]#òt7Ÿ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0µ^^š‰×êŠø™÷¦O]«İ»oŞ1¦29’YëŞØ®	år5¾]èz\\Y/l«ÎÄeÙ\"Îm]Zõ}|f—*ãšD2Menªåªö*OÒĞ•Œ]É¶+¹íJ×®ÒE(®½Ë]õZŞÊ½tÌVÙ¿£ñE¨XtM˜iïÿ3moÿ;Tt,“.èÇg…õkÜ¢ƒvH~pó÷ëMÇÏ»_<ŒğÏåó•uùJeçÊeºú“’İî¿+‚ª'),(100,100,200,600,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔĞ@g`+¤4‰\n¤4LÖ#ïDşÇøíÆ¦@¢å)(é¨(¹\0Ô‰P1^\' \nr‚ïH<zo~o>ı”ÖË^e5>·­:—uf‹¸4M|lš£æàÅ‰ŒkÉôLîæ¾^WYjzëU¢³ÃTæÅğÓªì¦fc’µº\"I]«‹TfŞ.Ue?\rÉ˜‘l3’ÜÖ¥Uß\'§¡bÑ5k?ÜvüåÕãÍÏ#‘®y²‚O\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0ŒZ/÷/ÌÆÄkuEüÔ{Ó§®Õîİ÷W_Í‡©LeÖº7¶kB¹\\Î†oºVVãsÛª3qYg¶ˆs[—V}ŸÚ¥Ê¸&‘LS™›j¹ª½Ê£4t%cW²íJnº’gµ«tŠkïrW½–·r\'³Uöoi|*]fÚûÿLÛÛÿÉ¤úñYaı\Z·è};$ß»>ıv}úòö#üsù|e]¾Ò#Ù¹t™®ş¤d7ûo\"´©Ğ'),(100,100,200,700,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHh ³à*¤4‰º4lÎ+ßFşÇzîbS ñ”é¨(=Ô<<\0ës¢ Oğ]éã•W3û›ığCvZ/{•ÕøÜ¶êL\\Ö™-âÒ4ñ±iŒšƒ\'2®I$Ó3¹›ûz]e©é­W‰ÎS™ÃO«²›^˜IÖêŠ$u­.R™y»T•ı4d$cF²ÍHr[—V}Ÿœ†ŠE×¬ıpÛñ§—7ß?^E\"]#òd7Ÿ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0µ^î_˜‰×êŠø©÷¦O]«İÛoŞ}1ï§29”Yë^Û®	år9¾]èzXYÏm«ÎÄeÙ\"Îm]Zõ}|j—*ãšD2Menªåªö*ÒĞ•Œ]É¶+¹éJÕ®ÒE(®½Ë]õJŞÈtÌVÙ¿¥ñy¨XtM˜iïÿ3moÿ;Tt$“.èÇg…õkÜ¢Ÿí|ïúôëõéçÛ_<ŒğÏåó•uùJdçÒeºú“’İì¿j”ª˜'),(100,100,200,800,'xÚíÏ?nÔ@Çñ·²)5di ³°RšD‚–ti˜¬GŞ‰üñÛMÄ1((ÓQQrPs$.@Åx€(È	¾#}<òè½ù½ùüSö\Z/¥ÕèÂ6êLTT©Í£ÂÔÑ‰©šÃ³SÖh,“sy˜ùjS¦‰é¬WŸ%2ËûŸFe?¹4[oÔåqâ\Z]$2õv¥*ó$dÄCF¼Ëˆ3[V}/CÅ¢­7¾¿íäë›çÛ_®Ç\"m-òb?Ÿ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\04^_š­‰6êòè¥÷¦K\\£í‡ïO>Ş˜OÉ´qïl[‡r¹šöß6t=-­F¶Qg¢¢Jme¶*¬ú.ZÚ•Ê°Fc™$23åj]y•gIèŠ‡®x×ßuÅ¯*Wê\"WŞe®|+ïåA2d«Ìïi|*mf:øÿL»Ûÿ5>–QtÃ³Âú5l“eÓ\'?º=ıv{:¿ÿÅıÿ\\>[[—­õXö®\\ªë?)éİşû©b'),(100,100,200,900,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔĞ@g`+¤4‰º4LÖ#ïDşÇøíÆ¦@Bâ)é¨((¸\0Ô‰P1^\' \nr‚ïH<zo~o>ş”ÖË^e5>·­:—uf‹¸4M|lš£æàÅ‰ŒkÉôLîæ¾^WYjzëU¢³ÃTæÅğÓªì¦fc’µº\"I]«‹TfŞ.Ue?\rÉ˜‘l3’ÜÖ¥Uß\'§¡bÑ5k?ÜvüååãÍO\"‘®y²‚O\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0ŒZ/÷/ÌÆÄkuEüÔ{Ó§®Õîí÷ï¿š«©LeÖº×¶kB¹\\Î†oºVVãsÛª3qYg¶ˆs[—V}ŸÚ¥Ê¸&‘LS™›j¹ª½Ê£4t%cW²íJnº’gµ«tŠkïrW½’7r\'³Uöoi|*]fÚûÿLÛÛÿÉ¤úñYaı\Z·éçvH¾w}úíúôİí/FøçòùÊº|¥G²sé2]ıIÉnößCª*'),(100,100,300,0,'xÚíÑ½nÓP€á/éÏ@…T‰ZX-±fBêÒÊUQa«„8mWIì“Öa@êe00²11rì\\÷€Ó´B”ş$9Ÿí7Ò›(ÏÉ÷|ù%+Y*ë‚C›¹Øı¤m{Aßƒ]3Ü2Îl½Ş“é«Ñ”¥Yë¤ÉhĞÌØ¦NšÛ‘¬ö&_2\'ëÑ±91áÈÅ½0Š3×Šd9µGÎÉfTìN÷Ï÷;6é[—ÃıâV>¥“§í~{ûääç×ÏM‘|(òt½ØxŞŞù>[ÃCO{‚‡¦ğĞzúŸxÔµëXàG]ÃCO×µÀºu<ğ¨[xèé¦xàQ—ncu	=İÖ<ªŞ],ğÀ£êá¡§»ZàGU›…xT5<ô4+<ğ¨Z³´Àª…‡fmUixàQ•ğĞÓ¼,ğÀ£ìÍÓ<Êzš·x”µEXàGYÃCO‹²À²µH<ğ([xèiÑxàQ–|XàGYÂCO¾,ğÀC{>-ğÀC{xèÉ·xhÍ·xhÎ·ú,ğÀC[¾çšó=<ôZà¡/ß3Á<ôå{îxè·ÀC_¾gƒšó=<êëá{Öxà¡5ßsÆÍù3å³ÀC_¾g…šó=/<êãá{¶xà¡5ßsÅÍùë­ÊRyplNL0rq/x‘¦fÅ™ËÏ~<üøİ|Z’Æ¶,gñ{›‹Ûåtyò«\r¬mæbô“¶í›ô­KÇÁ¾=r2}5š²Éªu“ÔÉã¨XNW…ç«ÂËUáË$¸Vqs’ÆxğN>È½hº·“Í+¾*îhåÃâLÿ>ÓùÓÿª¹#¼h<ı[Åµ7?>Ï&;ß¿¸úìâª\\ı\'Gøëá«]wºnGVNã¶ë^î\"íËÏßÖÄ	Í'),(100,100,300,100,'xÚíÑ±nÓPÅñ/Nš\n©3´,° û2!uiåª¨°UBÜÆWÉ­Û\\ß¤/ÄÀÈÆÄÈ°óH¼Nœ1Ğõ;IO¤«¦v®s~_Ë^åå0·!¾²Up&™ÄSSÆg¦<6Á¿:—öÕ‰¤{)û#_Ìò,5ëƒD—\'©ô\'Ë?ª éµ™›dÜ$I]©ô¼† GisFÒ‘¬ÎHF¶˜ÚàÉEsÅ .g~ùigßß<™ÿúö%©K‘§ÍÁû;Øù§½=vÛC{OzĞ9í=é±;ôÀK{CZĞ9íé±{ÚÒƒ¨iïGz §½=v×‚xioJz §½+=¶ßC{3zĞ5í½èAä´÷¢Çİ± ^ÚÓ‚ÈiïLíóĞŞˆô@M{zĞ9í}èqw-è—öæ´ rÚ»ÓßC{zĞÕC{í´÷§=ÓŞŸô@NÛ€ô@NÛxÚ ¥mA,íï=°¢V´ÀŠXÑ+z`E¬h=°¢VôÀŠXÑ+z`E¬è=°¢VôÀŠXÑ+z`E¬è-°¢VôÀŠXÑ+Z`E¬è-°¢V´ÀŠXÑ+Z`E¬h=°¢V´ÀŠXÑ+z`E */®ÍÜÄ³à&ñsïÍ\"uU¨?ü|øé‡ùÜ•Î‰ô*÷ÎÖes¹Üô–?ëæ®G¹\rñ•­‚3ñ´Èì$Ùbjƒ_Äv¤}u\"é¦Ò7ùp\\ø Óæ®¤½+Yİ•lîJ^.ƒæâÂ»‘ËßÊ{¹—¶g9ºåÆ—ÍƒºléğÿÏ´úô¿J§nZ´_«yïõúŸ«åÉ÷×ï>[¿›İş—ğÏ‡÷ÇÖÆáTön\\Æ›S$Ûüş:ô1Q'),(100,100,300,200,'xÚíÑÏjAÀñ_ş4‹Pğ¬­½”İÈIè¥eK¥z+ˆÓìLIv×ÙIºñ ø<zóäÑĞ³!øœd¤{„ø]ø$d3³¿Y¾ÈNíe¿°!¹´up&™•¹&3S%§¦:2Á=?“öêt¥w!»c_Î‹<3Këƒt/3LW?ê {Ù•Y˜tÜ4Í\\†™ô½… Yœ‘¶3ÒõŒtlË™\r~™ÇÃ¦šûÕÓN?¿|´øşéCW¤©DïÅÁ»áÿ=t¡….ôĞ…ºĞBzèB]è¡=t¡….ôĞ…ºĞCzèB]è¡-t¡‡.ôĞ…ºĞCZèB]è¡-t¡‡.´Ğ…ºĞCZèB\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0ü·j/÷®ÌÂ$óà¦ÉïÍ2suhŞ~»ÿî‹yß“Î±ôk÷Ú6U\\.×ıÕgw=(lH.mœIfen§ÉØ–3ü29·£ íÕéJ/“)F“Òy˜Å]i»+]ïJ·»Ò§¥+Â0..½»â•¼‘;Y;;ÈÁ-ŸÅÃ¦ŠgÚÿó™ÖOÿ}¨î‰tšhÙ¾V¼÷bóçÏz5ùîæîáæî×Ûßxu„L¬OÂ‰ì\\»<L¶S$ß~ÿŞÂmö'),(100,100,300,300,'xÚíÏ?nÔ@Çñ·²)5$4Ğ û\0[!¥Iä((¤‹„˜¬GŞ‰üñÛMÄ1RPÒQQrô‰;0^c!\nr‚ïHÆzo~o¾ş’½ÆËAi5º²:Ujó¨0utjê#£æèâL†5™ÊìRf¾Ú”ib:ëU¦—Ç‰,òşĞ¨ì\'×fkâº<N\\£ËDæŞ®Tå0	ñï2âÌV…UßÅç¡bÙÖßßvúıİóíÏo_¦\"m-òb?Ÿ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\04^_›­‰6êòè•÷¦K\\£í§OnïÌç™LeŞ¸¶­C¹ÜÌûoº–V£+Û¨3QQ¥62[V}Û•Ê°&S™%²0åj]y•gIèŠ‡®x×]ñëÊ•ºÅ•w™+ßËGyÙ*‡÷4¾	Ë¶3ü¦İí‡šÈ¤\rºáYáßÛqoúäGN/Çış÷#üsùbm]¶ÖÙ»q©®ÇIÇı7‰5¨@'),(100,100,300,400,'xÚíÏ¿nAÇññÙqBŠD\r	\r4èî\\!¥ItQPH	±ñ­ìî{cç‰7 ¥ ¤£JIICÏc ñìùl!\nòß•>·ÚÓÌşf¿ş–½ÆËAi5¾²:Ufó¸0u|jê#£æèâL†5Šd|)÷¾Z•Yj:ëU¢ËãT¦yhTöÓk³6ÉJ]¤®ÑY*oçªr˜†ŒdÈH6ÉÂV…Uß%ç¡bÖÖ+ßßvúíÍÓõ¯Û/‘H[‹<ÛÁg\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0—‡×fmâ•º<~á½éR×hûáç£O?Ìç±ŒeÒ¸w¶­C¹ÜLúoº—Vã+Û¨3qQe6¶*¬ú.>·s•a\"§25å|Yy•\'ièJ†®dÓ•ìº’—•+uŠ+ï®|+ïå^:d«ŞÑø*TÌÚ:Ìtğÿ™6·ÿ*:‘QtÃ³Â¿×Ûı{Ó\'?Øo÷w¿¸áŸË§KëK=‘½—ér—\"ÙnÿÑ©'),(100,100,300,500,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔĞ„ÙØ\n)M\"GA.b²y\'ò?Æo76Ç  ¤£¢äô‰0^g…(È	¾#}<òè½ù½ùòKöZ/•ÕøÊ¶êL\\Ö™-âÒ4ñ™išãWç2®I$ÓK¹Ÿûz]e©é­W‰.OR™ÃO«²Ÿ^›IÖêŠ$u­.R™y»T•Ã4d$cF²ÍHr[—V}Ÿ\\„ŠE×¬ıpÛÙ·7G›Ÿ_?G\"]#òt?Ÿ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0µ^^›‰×êŠø¹÷¦O]«İ‡>~7Ÿ¦29‘YëŞÙ®	år3¾]èz\\Y¯l«ÎÄeÙ\"Îm]Zõ}|a—*ãšD2Menªåªö*OÒĞ•Œ]É¶+Ùu%/jWé\"×Şå®z+ïå^:f«ŞÑø2T,º&Ìtğÿ™¶·ÿ*:•Iôã³ÂÙëqÚ!ùÁíé³Ûı÷İ/FøçòùÊº|¥§²wã2]íR$Ûí\0²]¨Ñ'),(100,100,300,600,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔĞ@ƒìl…”&‘£ @	1Y¼ùã·›‰–PPÒQQrzÄ¯³Bäß‘>yôŞüŞ|ı-{­—ƒÊj|i[u&.ëÌqišøÔ4GFÍÑË3×$’é…ÜÍ}½®²ÔôÖ«DÇ©Ì‹á§UÙO¯ÌÆ$kuE’ºV©Ì¼]ªÊa\Z2’1#Ùf$¹­K«¾OÎCÅ¢kÖ~¸íôû›Ç›_ß¾D\"]#òd?Ÿ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0µ^î_™‰×êŠø™÷¦O]«İ‡Ÿ>ı0Ÿ§29–YëŞÙ®	år=¾]èzXY/m«ÎÄeÙ\"Îm]Zõ}|n—*ãšD2Menªåªö*ÒĞ•Œ]É¶+Ùu%ÏkWé\"×Şå®z+ïåN:f«ŞÒø\"T,º&Ìtğÿ™¶·ÿ*:‘Iôã³ÂÙëq>¶Cò½›Ó§7§¯nñ0Â?—ÏWÖå+=‘½k—éj—\"Ùnÿ©¨š'),(100,100,300,700,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHh Aö\\…”&‘£ @	±9¯|ùë¹‹MÄcPPÒQQ\"zz	õ9\'DAà»ÒÇ+¯fö7ûù—ìµ^*«ñ¥mÕ™¸¬3[Ä¥iâSÓ5G/Ïd\\“H¦r7÷õºÊRÓ[¯]§2/†ŸVe?½2“¬ÕIêZ]¤2óv©*‡iÈHÆŒd›‘ä¶.­ú>9‹®Yûá¶Ó¯¯o~~ù‰tÈ“ı|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Ôz¹e6&^«+âgŞ›>u­vï<øğİ|œÊäXf­{k»&”Ëõløv¡ëae5¾´­:—uf‹8·uiÕ÷ñ¹]ªŒkÉ4•¹©–«Ú«<JCW2v%Û®d×•<¯]¥‹P\\{—»ê¼“;é˜­rxKã‹P±èš0ÓÁÿgÚŞşw¨èD&]ĞÏ\ng¯Æ=úİÉ÷nNŸŞœ~»ıÅÃÿ\\>_Y—¯ôDö®]¦«]Šd»ıño©b'),(100,100,300,800,'xÚíÏ1nÔ@Æñ·ŞÍ ¤HÔ¥ÙØ\n)M\"GAKºHˆÉzäÈk›ñÛMÄ1((é¨(szÄ¯³Bäÿ‘~yôŞ|o¾ı–ƒÆËQi5¾²:¯«ÌñÚÔñ™©šã‹sÖ(’ñ¥<Ì}µ)³ÔtÖ«D—\'©L‹ş§Q9L¯ÍÖ$uE’ºFç©L¼]ªÊ,\rÉ‘ì2’ÜVk«¾K¡bŞÖßßvvûîùö×÷¯‘H[‹¼8Áç\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0—Ç×fkâº\"~å½éR×hûéç“Ï?Ì—±ŒNdÒ¸¶­C¹ÜLúoº–Vã+Û¨3ñºÊlç¶Z[õ]¼°K•a\"§25årUy•gièJ†®d×•ì»’×•+uŠ+ïrW¾—ò ²Uf÷4¾	ó¶3ı¦İí‡ŠNeÔİğ¬pövØÇ‹¦O~twúòîtvÿ‹ûş¹|º²._é©Ü¸LWûÉöû‚	¨,'),(100,100,300,900,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔĞ@ƒìl…”&‘£ @	1Y¼ùã·›	‰KPPÒQQPpzÄ¯³Bäß‘>yôŞüŞ|ù-{­—ƒÊj|i[u&.ëÌqišøÔ4GFÍÑË3×$’é…ÜÍ}½®²ÔôÖ«DÇ©Ì‹á§UÙO¯ÌÆ$kuE’ºV©Ì¼]ªÊa\Z2’1#Ùf$¹­K«¾OÎCÅ¢kÖ~¸íôûëÇ›__?G\"]#òd?Ÿ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0µ^î_™‰×êŠø™÷¦O]«İûŸ>ş0Ÿ¦29–YëŞÚ®	år=¾]èzXY/m«ÎÄeÙ\"Îm]Zõ}|n—*ãšD2Menªåªö*ÒĞ•Œ]É¶+Ùu%ÏkWé\"×Şå®z#ïäN:f«ŞÒø\"T,º&Ìtğÿ™¶·ÿ*:‘Iôã³ÂÙ«qŸ~k‡ä{7§OoN?Üşâa„.Ÿ¯¬ËWz\"{×.ÓÕ.E²İşÉé¨ô'),(100,100,400,0,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHh ³%Ú«Ò$rèÒds^ù6ò?Öso@›‚’Š’’†ÇˆÄ;°>ß	Q\'ø®ô±åõÌşf¿ş–½ÖËAe5¾²­:—uf‹¸4M|jš#£æèí™kÉøBæ¾^VYjÖÖ«DÇ©L‹ş£UÙO¯ÍÊ$KuE’ºVg©L¼«Êa\Z2’!#Ùd$¹­K«~œ‡ŠY×,}Úé÷Ëç«»o_\"‘®y±‚Ï\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0Z/¯ÍÊÄKuEüÊ{³N]«İÇ_OnšÏcË¤uïm×„r¹™ôÏ.t=­¬ÆW¶Ugâ²Îlç¶.­úu|nç*Ã\ZE2Nejªù¢ö*ÏÒĞ•]É¦+Ùu%¯kWé,×Şå®z\'äA:d«ŞÓø&TÌº&Ìtğÿ™6§ÿ*:‘Q¬‡k…½ÛŸ/Û>ùÑv÷ÓvWî¿q?Â?‡OÖå=‘½—éb—\"Ùîı¶û¨®'),(100,100,400,100,'xÚíÏ½nÔ@†á³?Ù„‰\Z\ZèìØ\n)M\"GA!]\Z&ë‘w\"ÿ1>»±)¸*¤”tT))iè¹$îñzWˆ‚\\Á;ÒcËãsæ;óõ·ì5^J«Ñ•mÔ™¨¨R›G…©£SS5Gg2¬ÑX&—ò0óÕªLÓY¯2¾<Nd–÷Ê~rmÖ&^©ËãÄ5:OdêíBU“ñ&#ÎlUXõ]|*æm½òıi§ßŞ<_ÿºû2ik‘û!ø\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0À ñòøÚ¬M´R—G/½7]â\Zm?ü|rûÃ|ÈèX¦{gÛ:”ËÍ´¶¡ëii5º²:Ujó(³UaÕwÑ¹]¨k4–I\"3S.–•Wy–„®xèŠ7]ñ®+~U¹Rç¡¸ò.så[y/’![åğÆ×¡bŞÖa¦ƒÿÏ´9ıïPãµA7\\+ì}ßşüÔôÉ¶»·»éı7îGøçğÙÒºl©\'²wãR]îR$İ½ÿ\0şÛ©v'),(100,100,400,200,'xÚíÏ½nÔ@†á³?Ù„‰\Z\ZèìØ\n)M\"GA.\r“õÈ;‘ÿŸİØHÜ-%eÊ4PsHÜ\0ãõ®¹‚w¤Ç–ÇçÌwæË/Ùk¼”V£KÛ¨3QQ¥6\nSG§¦>2j^É°Fc™\\ÈıÌW«2MLg½Êøâ8‘YŞ4*ûÉ•Y›x¥.×è<‘©·U9LBF<dÄ›Œ8³UaÕwñy¨˜·õÊ÷§Ş¼~ºşùõóX¤­Eí‡à3\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0ƒÆËÃ+³6ÑJ]=÷Şt‰k´}ÿãÑÇoæÓDFÇ2mÜ[ÛÖ¡\\®§ı³\r]K«Ñ¥mÔ™¨¨R›G™­\n«¾‹ÎíBeX£±L™™r±¬¼Ê“$tÅCW¼éŠw]ñ‹Ê•:Å•w™+ßÈ;¹—Ù*‡w4¾ó¶3ü¦Íé‡\ZŸÈ¨\rºáZaïvûówÓ\'?Øî~Øî~¿ûÆıÿ>[Z—-õDö®]ªË]Š¤»÷FÊª>'),(100,100,400,300,'xÚíÏ¿nAÇññÙqBŠD\r	\r4èî\\!¥ItQPH	±ñ­ìî{cç‰7 ¥ ¤£JIICÏc ñìùl!\nòß•>·ÚÓÌşf¿ş–½ÆËAi5¾²:Ufó¸0u|jê#£æèâL†5Šd|)÷¾Z•Yj:ëU¢ËãT¦yhTöÓk³6ÉJ]¤®ÑY*oçªr˜†ŒdÈH6ÉÂV…Uß%ç¡bÖÖ+ßßvúíÍÓõ¯Û/‘H[‹<ÛÁg\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0—‡×fmâ•º<~á½éR×hûáç£O?Ìç±ŒeÒ¸w¶­C¹ÜLúoº—Vã+Û¨3qQe6¶*¬ú.>·s•a\"§25å|Yy•\'ièJ†®dÓ•ìº’—•+uŠ+ï®|+ïå^:d«ŞÑø*TÌÚ:Ìtğÿ™6·ÿ*:‘QtÃ³Â¿ïÛıuÓ\'?Ø>n÷çw¿¸áŸË§KëK=‘½—ér—\"Ùnÿ×U©'),(100,100,400,400,'xÚíÏ½nÔ@†á³?Ù„‰\Z\ZèìØ\n)M\"GA!]\Z&ë‘w\"ÿ1>»±)¸ZŠ”tT”)iè¹$îñ\ZQ+xGz<\ZëœùÎ|ù%{—ƒÒjteu&*ªÔæQaêèÔÔGFÍÑÅ™k2•Ù¥<Ì|µ)ÓÄtÖ«L/Yäı¡QÙO®ÍÖÄuyœ¸F—‰Ì½]©Êa2â!#ŞeÄ™­\n«¾‹ÏCÅ²­7¾¿íôîÍóíÏ¯Ÿ§\"m-òb?Ÿ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\04^_›­‰6êòè¥÷¦K\\£í‡O>}7·3™Ë¼qïl[‡r¹™÷ß6t=-­FW¶Qg¢¢Jme¶*¬ú.:·+•aM¦2KdaÊÕºò*Ï’Ğ]ñ®+»âW•+uŠ+ï2W¾•÷ò ²Uïi|*–mf:øÿL»Ûÿ5=‘ItÃ³Â¿oãŞôÉşœ>ûı/îGøçòÅÚºl­\'²wãR])’ûoD©Ğ'),(100,100,400,500,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHhBg?ÀUHi9\n\ntiØœW¾üõÜÅ¦@â\rh)(é¨()ièy$:€õ9\'DAà»ÒÇ+¯fö7ûù—ìµ^*«ñ•mÕ™¸¬3[Ä¥iâ3Ó5Ç/Ïe\\“H¦—r?÷õºÊRÓ[¯]¤2/†ŸVe?½6“¬ÕIêZ]¤2óv©*‡iÈHÆŒd›‘ä¶.­ú>¹‹®Yûá¶³¯¯6?¿|ŠDºFäé~>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00j½<¼6¯Õñ3ïMŸºV»w?}øn>Ner\"³Ö½±]Êåf6|»Ğõ¸²\Z_ÙV‰Ë:³EœÛº´êûøÂ.UÆ5‰dšÊÜTËUíU¤¡+»’mW²ëJ×®ÒE(®½Ë]õZŞÊ½tÌV9¼£ñE¨XtM˜éàÿ3moÿ;Tt*“.èÇg…³oãµCòƒÛÓ÷·ûï»_<ŒğÏåó•uùJOeïÆeºÚ¥H¶Ûÿ\0\0Œ©™'),(100,100,400,600,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHh ³à*¤4‰\n¤4lÎ+ßFşÇzîbS ÑRÑRPÒQQRÒĞóH¼ësNˆ‚<Áw¥W^ÍìoöËoÙk½TVãKÛª3qYg¶ˆKÓÄ§¦92j^œÉ¸&‘L/änîëu•¥¦·^%º8Ne^?­Ê~ze6&Y«+’ÔµºHeæíRUÓ‘ŒÉ6#Ém]Zõ}r*]³öÃm§ß^=Şüúú9é\Z‘\'û!ø\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0À¨õrÿÊlL¼VWÄO½7}êZíŞı|ğñ‡ù4•É±ÌZ÷ÆvM(—ëÙğíB×ÃÊj|i[u&.ëÌqnëÒªïãs»T×$’i*sS-WµWy”†®dìJ¶]É®+yV»J¡¸ö.wÕky+wÒ1[åğ–Æç¡bÑ5a¦ƒÿÏ´½ıïPÑ‰Lº ŸÎ¾{ô¾’ïİœ~¸9}yû‹‡ş¹|¾²._é‰ì]»LW»Évû÷¯©b'),(100,100,400,700,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHh ³à*¤4‰º4lÎ+ßFşÇzîbS ñ´”tT”ˆŠ†Ç@‚g`}Î	Q\'ø®ôñÊ«™ıÍ~ú%{­—ƒÊj|i[u&.ëÌqišøÔ4GFÍÑ‹3×$’é…ÜÍ}½®²ÔôÖ«DÇ©Ì‹á§UÙO¯ÌÆ$kuE’ºV©Ì¼]ªÊa\Z2’1#Ùf$¹­K«¾OÎCÅ¢kÖ~¸íôËËÇ›ŸŸ?F\"]#òd?Ÿ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0µ^î_™‰×êŠø©÷¦O]«İÛŞ7¦29–Yë^Û®	år=¾]èzXY/m«ÎÄeÙ\"Îm]Zõ}|n—*ãšD2Menªåªö*ÒĞ•Œ]É¶+Ùu%ÏjWé\"×Şå®z%oäN:f«ŞÒø<T,º&Ìtğÿ™¶·ÿ*:‘Iôã³ÂÙ·q~·Cò½›Ów7§_oñ0Â?—ÏWÖå+=‘½k—éj—\"Ùnÿ?ª*'),(100,100,400,800,'xÚíÏ½nÔ@†á³?Ù„‰\Z²4ĞÙ°RšD‚º4LÖ#ïDşc|vcS q´”tT”)ÓĞsHÜãuVˆ‚\\Á;Òã‘GçÌwæÛo9h¼•V£KÛ¨3QQ¥6\nSGg¦>6jßœË°Fc™\\ÈÃÌW›2MLg½Êøâ$‘YŞÿ4*‡É•Ùšx£.×è\"‘©·+U™\'!#2â]FœÙª°ê»x*m½ñımg7oŸo}ÿ:ik‘‡!ø\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0À ñòøÊlM´Q—G/½7]â\Zm?ş|òù‡ù2‘Ñ‰L÷Ş¶u(—ëiÿmC×ÓÒjtiu&*ªÔæQf«Âªï¢¥]©k4–I\"3S®Ö•Wy–„®xèŠw]ñ¾+~U¹R¡¸ò.så;ù ’![e~OãëP±hë0ÓÑÿgÚİşw¨ñ©ŒÚ În‡}²lúäGw§ŸîNç÷¿¸áŸËgkë²µÊÁµKu½O‘t¿ÿĞ)¨ô'),(100,100,400,900,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHh ³à*¤4‰º4lÎ+ßFşÇzîbS !ñ\0´”tT”4ô<ïÀúœ¢ Oğ]éã•W3û›ıü[öZ/•ÕøÒ¶êL\\Ö™-âÒ4ñ©iŒš£g2®I$Ó¹›ûz]e©é­W‰.S™ÃO«²Ÿ^™IÖêŠ$u­.R™y»T•Ã4d$cF²ÍHr[—V}Ÿœ‡ŠE×¬ıpÛé·—7¿¾|ŠDºFäÉ~>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00j½Ü¿2¯ÕñSïMŸºV»·?|øa>Ner,³Ö½¶]Êåz6|»Ğõ°²\Z_ÚV‰Ë:³EœÛº´êûøÜ.UÆ5‰dšÊÜTËUíU¥¡+»’mW²ëJÕ®ÒE(®½Ë]õJŞÈtÌV9¼¥ñy¨XtM˜éàÿ3moÿ;Tt\"“.èÇg…³ïã>ıÚÉ÷nNßßœ¾»ıÅÃÿ\\>_Y—¯ôDö®]¦«]Šd»ı©¼'),(100,100,500,0,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHhB·–h¯BJ“ÈQP K“ÍyåÛÈÿXÏ]|H<EJ:*J^€Ç@¢Ë`Ÿï„(È|Wúxe{f³_Ë^ä tj®]£Şš¢J]n\n[›3[[µÇïÎeX£HÆ—ò8Õ²L»vA%º<Idš÷/Ê~rcW6^ªÏãÄ7:KdÜ\\U“.#2âMFœ¹ªp\ZÖñEW1këeèO;û~u´úõíK$ÒÖ\"/÷»às\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0ƒ&ÈÓ»²f©>7¯C°ëÄ7Ú~úùìó{7–Ñ‰L\ZÿÁµuW.·“şÙv]ÏK§æÚ5ê­)ªÔå&sUá4¬Í…›«kÉ8‘©-ç‹*¨¼Hº®xèŠ7]ñ®+~SùRg]q|æË÷òQ%C¶Êáo»ŠY[w3ü¦Íé‡ŠNeÔvÖÃµD¢£íÏWMŸüd[y¿ı*ß¸áŸÃ§ç³…ÊŞ­Ou±K‘t·ÿ–‹¨w'),(100,100,500,100,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔĞ„Î>ÀVHi9\n\néÒ0Y¼ùã·/—@¢HIGE™Ğs$:Àx½+DANğéã‘í÷æ÷æë/Ùk½TVãkÛª3qYg¶ˆKÓÄg¦96j/ÏeX£HÆWò8÷õ²ÊR³¶^%º:IeZô/­Ê~zcV&Yª+’Ôµ:Keâí\\UÓ‘É&#Ém]Zõëä\"TÌºféûÓÎîß­~~û‰tÈËı|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`ĞzyzcV&^ª+âWŞ›uêZí>şxöù»¹ËèD&­{o»&”Ëí¤v¡ëye5¾¶­:—uf‹8·uiÕ¯ã;WÖ(’q*SSÍµWy‘†®dèJ6]É®+y]»Jg¡¸ö.wÕ;ù Ò![åğÆ7¡bÖ5a¦ƒÿÏ´9ıïPÑ©Œº`=\\K$:ÚşüÔöÉO¶•¿·_³‡oÜğÏáÓ…uùBOeïÖeºØ¥H¶Ûÿ\0Şk©?'),(100,100,500,200,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHhBg?ÀUHimèÒ°9¯|ùë¹‹‰Ç  ¤£¢ä’šÇ@¢£¢Â>ß	Q\'ø®ôñÊöÌşf¿ü”½&ÈAé4¾rzUêò¸°u|fëc«öøõ¹kÉøRf¡Z–©±kT¢Ë#Ó¼iTöÍµ]Ùd©>OŒotfdÜ\\UM—‘É&#É\\U8\rëä¢«˜µõ2ô§}{s´úñõs$ÒÖ\"Ï÷»às\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0ƒ&Èãk»²ñR}¿Á®o´ığıÉÇ[ûi,£™4şkë®\\n&ı³íº–Nã+×¨·qQ¥.3WNÃ:¾ps•a\"™Úr¾¨‚Ê3Óu%CW²éJv]ÉËÊ—:ëŠ«à3_¾•÷òÀÙ*‡÷4¾ê*fmİÍtğÿ™6§ÿ*:•QÛY×‰¶?7}ò£må¯í×»ûoÜğÏáÓ…óÙBOeïÆ§ºØ¥HºÛÿ\0&Zª'),(100,100,500,300,'xÚíÏ?nAÇñçµã„‰\ZšĞ İ¸BJ“h¢ .bâ­\'ÚÌ>;k\n$AAIGE™Ğs$:À®×¢ \'øôÙÑhß›ß›¯¿d¯	rP:¯]£ŞÆE•º<.lŸÙúØª=¾<—a\"_ÉÃ,TË25ví‚Jtubdš÷‡FeßÜØ•M–êóÄøFgF&ÁÍUåĞtÉ‘l2’ÌU…Ó°N.ºŠY[/CÛÙİÛ£ÕÏo_\"‘¶y¾ßŸ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\04AßØ•—êóøevm|£íÇO>}·ŸÇ2:‘Iãß»¶îÊåvÒÛ®ëié4¾vzUêò8sUá4¬ã7WÖ(’±‘©-ç‹*¨<3]W2t%›®d×•¼ª|©³®¸\n>óå;ù Ì­rxOãë®bÖÖİLÿŸisûß¡¢Sµõğ,‘èhûïMÓ\'?Ú~o÷÷¿¸áŸË§ç³…ÊŞ­Ou±K‘t·ÿ¶å¨Ñ'),(100,100,500,400,'xÚíÏ¿nAÇññÙqBŠD\r	MèîÀRšDº4l|«óF÷½±s¦@â\rh)(é¨(S¦¡ç1èx\0î|¶y‚ïJŸ[­nf³_É^ä t\Z_¹F½‹*uy\\Ø:>³õ±U{üú\\†5Šd|)³P-ËÔØµ*Ñå‰‘iŞ\Z•}smW6YªÏã™7W•CÓe$CF²ÉH2WNÃ:¹è*fm½ımg·oV?¿}‰DÚZäù~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Ğy|mW6^ªÏã!Øµñ¶~<ùôİ~ËèD&çÚº+—›Iÿm»®§¥ÓøÊ5êm\\T©ËãÌU…Ó°/Ü\\eX£HÆF¦¶œ/ª òÌt]ÉĞ•lº’]Wò²ò¥Îºâ*øÌ—oå½<0C¶Êá=¯ºŠY[w3ü¦Íí‡ŠNeÔvÖÃ³D¢£í¿»¦O~´=ıŞîïq?Â?—OÎg=•½Ÿêb—\"énÿşÅ©™'),(100,100,500,500,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHhBg?ÀUHi9\n\néÒ°9¯|ùë¹‹‰Ç  LGEÉ¤ç1èx\0Ög,DAà»ÒÇ+K3ó›ıòSöZ/•ÕøÚ¶êL\\Ö™-âÒ4ñ™išãËsÎ$’é•<Î}½®²Ôl­W‰®NR™ıO«²ŸŞ˜IÖêŠ$u­.R™y»T•Ã4d$CF²ËHr[—Vı6¹‹®Yû~ÚÙ··G›_ï\"‘®y¹‚Ï\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0Z/OoÌÆÄkuEüÊ{³M]«İÇïÏ>İ›ÏS™œÈ¬uïm×„r¹õß.t=¯¬Æ×¶Ugâ²Îlç¶.­úm|a—*Ã™D2Menªåªö*/ÒĞ•]É®+»’×µ«tŠkïrW½“ò(²Uh|*]v:øÿN»é—ŠNeÒÛáY\"ÑÑx·}ò“?•¿Æûá÷+ü3|¾²._é©ìİºLWcŠdãıà\r©b'),(100,100,500,600,'xÚíÏ?nAÇñçµã„‰\ZšĞÍÀRšD\n¤4L¼£õDûÙggMDË\r((é¨(¹\0=Ç@¢ã\0ìzmE)È	¾#}v4«÷æ÷æÛÙk‚”NÍ•kÔ[ST©ËMaksfëc«öøõ¹kÉøRf¡Z–ib×.¨D—\'‰LóşĞ¨ì\'×veã¥ú<N|£³D&ÁÍUå0é2â!#ŞdÄ™«\n§a_t³¶^†ş¶³oV¿¿DÚZäù~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Ğy|mWÖ,ÕçæEvøFÛ¿|şi¿Œet\"“Æ¿wmİ•ËÍ¤ÿ¶]×ÓÒ©¹rzkŠ*u¹É\\U8\rksáæ*Ã\ZE2NdjËù¢\n*Ï’®+ºâMW¼ëŠ_V¾ÔYW\\Ÿùò|É­rxOã«®bÖÖİLÿŸisûíPÑ©ŒÚÎzx–Ht´İ?5}ò£måßíß7÷¿¸áÎåÓ…óÙBOeïÆ§ºØ¥HºÛÿ×?©+'),(100,100,500,700,'xÚíÏ¿nAÇññÙqBŠD\r	MèîÀRšDº4l|«óF÷½±s¦@â1((é¨(Qúô<T<\0w>[ˆ‚<Áw¥Ï­ö4³¿Ù/?e¯	rP:¯\\£ŞÆE•º<.lŸÙúØª=~}.Ã\ZE2¾”‡Y¨–ejìÚ•èòÄÈ4ïÊ¾¹¶+›,Õç‰ñÎŒL‚›«Ê¡é2’!#Ùd$™«\n§a\\t³¶^†ş¶³ooV?¾~DÚZäù~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Ğy|mW6^ªÏã!Øµñ¶¾?ùxg?et\"“Æ¿smİ•ËÍ¤ÿ¶]×ÓÒi|å\Zõ6.ªÔåqæªÂiXÇn®2¬Q$c#S[ÎUPyfº®dèJ6]É®+yYùRg]q|æË·ò^˜![åğÆW]Å¬­»™ş?Óæö¿CE§2j;ëáY\"ÑÑvÿÕôÉ¶•¿·oïq?Â?—OÎg=•½Ÿêb—\"énÿ.©ó'),(100,100,500,800,'xÚíÏ1nÔ@Æñ·ŞÍ ¤HÔ¥	}€­Ò$š(h¡KÃd=òNäµÍøíÆ¦@â”tT”\\€c Ñq\0ìuV(Er‚ÿH?Æzo¾7ßşÈAä¨p\Z_¹Z½×eêòxm«øÜV\'VíÉÛÖ(’ñ¥<ÎB¹)Rc[T¢ËS#Ó¼?Ô*‡æÚnm²QŸ\'Æ×:72	n©*3Óe$CF²ËH2W®†6Ytó¦Ú„ş¶óï·¿¿DšJäåa|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Pyzm·6Ş¨ÏãW!ØÖøZ›O¿}şi¿Œet*“ÚpMÕ•ËÍ¤ÿ6]×óÂi|åjõ6^—©ËãÌ•k§¡n©2¬Q$c#S[,WePyaº®dèJv]É¾+y]úBç]q|æ‹÷òQ™![eö@ã›®bŞTİLG÷Ï´»ıÿPÑ™ŒšN;<K$:öñ¢î“ŸÜVş½ı;{øÅıw.Ÿ®œÏVz&7>ÕÕ>EÒış¯¹¨½'),(100,100,500,900,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔĞ„Î>ÀVHiMèÒ0Y¼ùã·›	‰KPPÒQQPpzDÇ°×Y!\nr‚ïHÆzo~o>ÿ’½&ÈAé4¾rzUêò¸°u|fëc«öøå¹ŒkÉôRîg¡Z—©±*Ñå‰‘y>\Z•}sm76Y«Ïã]™·T•CÓg$cF²ÍH2WNC—\\ô‹¶^‡á¶³o¯6?¿|ŠDÚZäé~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Ôyxm76^«Ïãg!ØÎøFÛw?}øn?Ner\"³Æ¿qmİ—ËÍlø¶}×ãÒi|å\Zõ6.ªÔåqæªÂièâ·T×$’©‘¹-—«*¨<1}W2v%Û®d×•<¯|©‹¾¸\n>óåky+÷Ì˜­rxGã‹¾bÑÖıLÿŸi{ûß¡¢S™´½n|–Ht4îÓ¯Íüà¶ò÷íß÷w¿xáŸËç+ç³•ÊŞOuµK‘t·ÿ÷™©…'),(100,100,600,0,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHh ³%Ú«Ò$r”P ¥as^ù6ò?ÖsmŞ€‚2%/@Ïc ñ¬ÏwBä	¾+}ly=³¿Ù¯¿e¯õrPY¯l«ÎÄeÙ\".MŸšæÈ¨9zs&Ã\ZE2¾”‡¹¯—U–šµõ*Ñåq*Ó¢ÿhUöÓk³2ÉR]‘¤®ÕY*oçªr˜†ŒdÈH6InëÒª_\'ç¡bÖ5KßŸvúıİóÕ¯ow‘H×ˆ¼ØÁg\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0­—Ç×feâ¥º\"~å½Y§®ÕîÓÏ\'Ÿ˜/cË¤ul×„r¹™ôÏ.t=­¬ÆW¶Ugâ²Îlç¶.­úu|nç*Ã\ZE2Nejªù¢ö*ÏÒĞ•]É¦+Ùu%¯kWé,×Şå®z/åA:d«ŞÓx*f]f:øÿL›ÓÿÈ¨ÖÃµD¢ÛíÏ—mŸüh»ûv»+÷ß¸áŸÃ§ëò…ÈŞËt±K‘l÷şŒ¨@'),(100,100,600,100,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔĞ@g`+¤4‰…Hi˜¬GŞ‰üñÛ—‰–D¢ ¤KEÉè9w`¼Ş¢ \'øô±åñ{ó{s÷[öZ/•ÕøÊ¶êL\\Ö™-âÒ4ñ©iŒš£‹3Ö(’ñ¥<Ì}½¬²Ô¬­W‰.S™ıG«²Ÿ^›•I–êŠ$u­ÎR™x;W•Ã4d$CF²ÉHr[—Vı:9³®Yúş´ÓïoŸ¯~}û\Z‰tÈ‹ı|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Ğzy|mV&^ª+â—Ş›uêZí>ş|òù‡ù2–Ñ±LZ÷ŞvM(—›IÿìB×ÓÊj|e[u&.ëÌqnëÒª_Ççv®2¬Q$ãT¦¦š/j¯ò,\r]ÉĞ•lº’]Wòªv•ÎBqí]îªwòA¤C¶Êá=¯CÅ¬kÂLÿŸisúß¡¢uÁz¸–Hôiûó¶í“mwßlw³ûoÜğÏáÓ…uùBOdïÆeºØ¥H¶{ÿÓå©'),(100,100,600,200,'xÚíÏ?nÔ@Çñ·ŞÍDH‘¨!¡!}€­Ò$r(Ò0Y¼­ÿ0~»±)h¹%%€šc q*Æë]!\nr‚ïH[¿7¿7ŸÉ^ãå°´\Z_ÛF‰‹*³Ë¸0u|nê£æäÅ…kÉøJös_­Ê,5õ*ÑÕi*ÓeÿÑ¨¤7fm’•ºe’ºFg©L¼«ÊQ\Z2’!#Ùd$¹­\n«¾K.CÅ¬­W¾?íüë«\'ëŸ_>E\"m-r|‚/\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\Z/nÌÚÄ+uËø©÷¦K]£í»?|3Ç2:•IãŞØ¶år;éŸmèzTZ¯m£ÎÄE•ÙeœÛª°ê»øÒÎU†5ŠdœÊÔ”óEåU§¡+º’MW²ëJU®ÔY(®¼Ë]ùZŞÊ½tÈV9º£ñy¨˜µu˜éğÿ3mNÿ;Tt&£6è†k‰Dï·?7}òıíîËíî÷»oÜğÏáÓ…uùBÏdïÖeºØ¥H¶{ÿÔ©Ğ'),(100,100,600,300,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔĞ@ƒ<Ø\n)M\"GA!R$Äd=òNäŒßn¼H´Ü€‚’.% çHÜ{½+DANğécËã÷æ÷æö·ì5AJ§ñ•kÔÛ¸¨R—Ç…­ãS[YµGg2¬Q$ãK¹Ÿ…jY¦‰]» ]\'2ÍûFe?¹¶+k–ês“øFg‰L‚›«ÊaÒe˜!Ãl2LæªÂiX›ó®bÖÖËĞŸvúıíÓÕ¯o_#‘¶y¶ßŸ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\04A^Û•—êóøEvøFÛ?}şa¿Œet,“Æ¿wmİ•ËÍ¤¶]×ãÒi|å\Zõ6.ªÔåqæªÂiXÇçn®2¬Q$ãD¦¶œ/ª ò$éºÌĞe6]f×e^V¾ÔYW\\Ÿùò|{É­rxGã«®bÖÖİLÿŸisúß¡¢µõp-‘èÓöß›¦O~°İ}½İ}~÷ûş9|ºp>[è‰ìİøT»Iwï?¬_¨š'),(100,100,600,400,'xÚíÏ¿nÔ@Çñ9ßå\n\"¤HÔĞÎ~€«Ò$r(Ò°9¯|ÿ°»ØH´T´”tT””4ô<ïÀú|\'DAà»ÒÇ–×3û›ıò[ö\Z/‡¥ÕøÚ6êL\\T™]Æ…©ãsSŸ5\'/.dX£HÆW²ŸûjUf©é¬W‰®NS™.ûFå ½1k“¬Ô-“Ô5:Keâí\\UÒ‘É&#ÉmUXõ]r*fm½òıiçß^=Yÿúú9ik‘ãƒ|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`ĞxypcÖ&^©[ÆO½7]ê\Zmßı|øñ‡ù4–Ñ©L\Z÷Æ¶u(—ÛIÿlC×£Òj|mu&.ªÌ.ãÜV…UßÅ—v®2¬Q$ãT¦¦œ/*¯ò8\r]ÉĞ•lº’]Wò¬r¥ÎBqå]îÊ×òVî¥C¶ÊÑÏCÅ¬­ÃL‡ÿŸisúß¡¢3µA7\\K$z¿ı÷½é“ïow_nw?Ü}ã~„Ÿ.¬Ëz&{·.ÓÅ.E²İûô?©b'),(100,100,600,500,'xÚíÏ?nAÇñçµã„‰\ZšĞÍÀRšD\n¤4L¼£õDûÙggMDË\r((é¨(¹\0=Ç@¢ã\0ìzmE)È	¾#}f4Ò{ó{óíì5AJ§æÊ5ê­)ªÔå¦°µ9³õ±U{üú\\†5Šd|)³P-Ë4±kT¢Ë“D¦yiTö“k»²ñR}\'¾ÑY\"“àæªr˜tño2âÌU…Ó°/ºŠY[/CÿÚÙ·G«ßß¿F\"m-ò|¿>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00h‚<¾¶+k–êsó\"»N|£íÇ_O>ÿ´_Æ2:‘Iãß»¶îÊåfÒïm×õ´tj®\\£Şš¢J]n2WNÃÚ\\¸¹Ê°F‘Œ™Úr¾¨‚Ê³¤ëŠ‡®xÓïºâ—•/uÖWÁg¾|\'äA2d«ŞÓøª«˜µu7ÓÁÿgÚ¼~;Tt*£¶³¾%}ÚGMŸüh{{³íø{ÿûî<>]8Ÿ-ôTön|ª‹]Š¤»óÕ‡©+'),(100,100,600,600,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔĞ@7>ÀVHi9\n\n)Ò0Y¼ùã·/-7HAIG•’Ğs$î€½ÆBäß‘>3\Zé½ù½ùúKöš ¥Sså\ZõÖUêrSØÚœÚúÈª=º8“aM\"™^ÊÃ,Të2MìÖ•èò8‘yŞ_\Z•ıäÚnl¼VŸÇ‰ot‘È,¸¥ª&]F<dÄ»Œ8sUá4lãó®bÑÖëĞ¿vúííóÍÏ»/‘H[‹¼Øï‚Ï\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0š ¯íÆšµúÜ¼ÁnßhûñÇ“ÛïöóT&Ç2kü{×Ö]¹ÜÌú½íº–NÍ•kÔ[ST©ËMæªÂiØšs·TÖ$’i\"s[.WUPy–t]ñĞïºâ±+~UùR]q|æËwòA$C¶Êá=¯»ŠE[w3ü¦İë‡ŠNdÒv¶Ã·D¢OãÙôÉşÜŞŒçı?îGøçñùÊùl¥\'²wãS])’çoÌ¹¨ô'),(100,100,600,700,'xÚíÏ¿nAÇññÙqA„‰\Z\ZÒİ=€+¤4‰.\n\nHiØøVç|Ø;wH´¼%%¢§ç1àØóÙBä	¾+}vµÒÌşf?ÿ’½ÆËai5¾¶:Uf—qaêøÜÔ\'FÍÉ‹Ö(’ñ•ìç¾Z•Yj:ëU¢«ÓT¦ËşÒ¨¤7fm’•ºe’ºFg©L¼«ÊQ\Z2’!#Ùd$¹­\n«¾K.CÅ¬­W¾íüë«\'ëŸ_>E\"m-r|‚/\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\Z/nÌÚÄ+uËø©÷¦K]£í»?|7Ç2:•IãŞØ¶år;é÷6t=*­Æ×¶Qgâ¢Êì2ÎmUXõ]|iç*Ã\ZE2NejÊù¢ò*ÓĞ•]É¦+Ùu%Ï*Wê,WŞå®|-oå^:d«İÑø<TÌÚ:Ìtøÿ™6¯ÿ*:“QtÃ·D¢÷ÛówÓ\'ßßŞ^nÏowÿ¸áŸÇ§ëò…ÉŞ­Ët±K‘lwş¨©¼'),(100,100,600,800,'xÚíÏ?nAÇñçµã„‰\Zb\Zèvà\n)M¢‚RšL¼£õDûÙggMDË\r((é¨(¹\0=Ç@ÊØõÆBäß‘>;ÚÑ{ó{óíVš G¥ÓøÊ5êm\\T©ËãÂÖñ™­­Úã7ç2¬Q$ãy˜…j]¦‰İº ]œ$2ÍûŸFå0¹¶kÖês“øFç‰L‚[ªÊ,é2Ìav&sUá4lÍ¢«˜·õ:ô·ı¸|¾ùıık$ÒÖ\"/»às\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0ƒ&Èãk»±ñZ}¿Ánßhûñ×“Ï?í—±ŒNdÒø÷®­»r¹™ôß¶ëzZ:¯\\£ŞÆE•º<Î\\U8\rÛxá–*Ã\ZE2NdjËåª\n*Ï’®Ë]f×eö]æUåKwÅUğ™/ßÉyÙ*³{\Z_wó¶îf:úÿL»ÛÿÊ¨íl‡g‰DŸ†}¼húäGw§oïNg÷¿¸áŸË§+ç³•ÊÁOuµO‘t¿ÿ¥3¨†'),(100,100,600,900,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔĞ@ç9ÀVHi9\n\nHi˜¬GŞ‰üñÛM„DÅ\r((é¨((¸\0=Ç@âØë¬9Áw¤G½7¿7_~Ë^ä t\Z_ºF½‹*uy\\Ø:>µõ‘U{ôâLÆ5‰dz!w³P­Ë4±*ÑÅq\"ó|øiTö“+»±f­>7‰ot‘È,¸¥ª&}†3Ì6Ãd®*œ†Îœ÷‹¶^‡á¶Óï¯o~}ı‰´µÈ“ı>ø\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0À¨	rÿÊnl¼VŸÇOC°]âmßı|ğñ‡ı4•É±Ì\ZÿÆµu_.×³áÛö]K§ñ¥kÔÛ¸¨R—Ç™«\n§¡‹ÏİRe\\“H¦‰Ìm¹\\UAåQÒw™±Ël»Ì®Ë<«|©‹¾¸\n>óåky+w’1[åğ–Æç}Å¢­û™ş?Óöö¿CE\'2i{İø,‘èÃ¸O¿5Cò½›Ó—7§ïoñ0Â?—ÏWÎg+=‘½kŸêj—\"énÿí©N'),(100,100,700,0,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHh ³%Ú«Ò$Ú((Ğ¥Éæ¼òmä¬ç.>\n$ƒ\"%%¢§ç1Â3`Ÿï„(È|WúØòzf³_îd¯	rP:¯\\£ŞÆE•º<.lŸÚúÈª=z{&Ã\ZE2¾‡Y¨–ejìÚ•èâØÈ4ï?\Z•}smW6YªÏã™7W•CÓe$CF²ÉH2WNÃ:9ï*fm½ıi§ß.Ÿ¯~}ı‰´µÈ‹ı.ø\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0À 	òøÚ®l¼TŸÇ¯B°kãm?ş|òé‡½ËèX&ïÚº+—›Iÿl»®§¥ÓøÊ5êm\\T©ËãÌU…Ó°Ïİ\\eX£HÆF¦¶œ/ª òÌt]ÉĞ•lº’]Wòºò¥Îºâ*øÌ—ïäƒ<0C¶Êá=oºŠY[w3ü¦Íé‡ŠNdÔvÖÃµD¢ßÛŸ/›>ùÑv÷ûvWî¿q?Â?‡OÎg=‘½Ÿêb—\"éîıÚ%©'),(100,100,700,100,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHh ³à*¤4‰6\n\néÒ°9¯|ùë¹‹‰—@¢HIGEÑÓóHğØç;!\nòß•>¶¼Ùßì—_²×9(ÆW®Qoã¢J]¶Om}dÕ]œÉ°F‘Œ/åaªe™\Z»vA%º<62ÍûFeß\\Û•M–êóÄøFgF&ÁÍUåĞtÉ‘l2’ÌU…Ó°NÎ»ŠY[/CÚéİ›ç«Ÿ_?G\"m-òb¿>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00h‚<¾¶+/ÕçñËìÚøFÛ?|únoÇ2:–Iãß¹¶îÊåfÒ?Û®ëié4¾rzUêò8sUá4¬ãs7WÖ(’±‘©-ç‹*¨<3]W2t%›®d×•¼ª|©³®¸\n>óå[y/Ì­rxOãë®bÖÖİLÿŸisúß¡¢µõp-‘è÷öçÇ¦O~´İı¶İMï¿q?Â?‡OÎg=‘½Ÿêb—\"éîı\"©Ğ'),(100,100,700,200,'xÚíÏ±nÔ@Æñ9ßå\nR$jHh [?ÀUHi9\n\ntiØœW¾|¶YÏ]l\n$ƒ‚’Š¥5@…}¾¢ Oğ_égËë™ıf?ÿ”½:ÈAáÔ\\ºZ½5Ë2u¹YÚÊœÚêÈª=zu&Ã\ZE2¾ûY(WEšØÖ•èâ8‘iŞÔ*ûÉ•]Ûx¥>_ë,‘IpsU9LºŒxÈˆ7qæÊ¥ÓĞÆç]Å¬©V¡?íôëë§ë_>E\"M%òl¿>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00¨ƒ<¼²kkVêsó<Û&¾Öæı÷GníÇ±ŒeRû·®©ºr¹ôÏ¦ëz\\85—®VoÍ²L]n2W.†Öœ»¹Ê°F‘Œ™Úb¾(ƒÊ“¤ëŠ‡®xÓïºâ¥/tÖ—Ág¾x#ïä^2d«ŞÑø²«˜5U7ÓÁÿgÚœşw¨èDFM§®%ıÚşü]÷É¶»7Ûİowß¸áŸÃ§ç³…ÈŞµOu±K‘t÷şiôª˜'),(100,100,700,300,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHh Aö\\…”&ÑFA!]$Äæ¼ºÛÈÿXÏ]l\n$ƒ‚’Š2¢§ç1à°ÏwBä	¾+}ly=³¿Ù/¿d¯rP8¯\\­ŞÆy™º,ÎmŸÚêÈª=º8“a\"_ÊıE(WEjlë‚JtyldšõµÊ¾¹¶k›¬Ôg‰ñµÎŒL‚›«Ê¡é2’!#Ùd$WæNC›œw³¦Z…ş´ÓÛ7O×?¿~DšJäÙ~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Pyxm×6^©Ïâ!ØÖøZ›?}ün?et,“Ú¿sMÕ•ËÍ¤6]×ãÂi|åjõ6ÎËÔeñÂ•¹ÓĞÆçn®2¬Q$c#S[Ì—ePybº®dèJ6]É®+yYúBg]qüÂoå½Ü3C¶Êá¯ºŠYSu3ü¦Íé‡ŠNdÔtÚáZ\"Ñïí¿×uŸü`»ûm»ûüî÷#üsøtéüb©\'²wãS]îR$İ½ÿ\0ú©b'),(100,100,700,400,'xÚíÏ±nÔ@Æñ9ßå\nR$jHh [?ÀUHi9\n\ntiØœW¾|¶YÏ]l\n$Ş€–‚’Š2JECÏc Á3`Ÿï„(ÈüWúÙòzf¿Ù/¿d¯rP85—®VoÍ²L]n–¶2§¶:²j^É°F‘Œ/ä~ÊU‘&¶uA%º8Ndš÷µÊ~re×6^©ÏãÄ×:KdÜ\\U“.#2âMFœ¹ré4´ñyW1kªUèO;½yıtıóëçH¤©DíwÁg\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0u‡WvmÍJ}n‡`ÛÄ×Ú¼ÿñèãwûi,£c™Ôş­kª®\\®\'ı³éºNÍ¥«Õ[³,S—›Ì•K§¡5çn®2¬Q$ãD¦¶˜/Ê ò$éºâ¡+ŞtÅ»®øEéuÅeğ™/ŞÈ;¹—Ù*‡w4¾ì*fMÕÍtğÿ™6§ÿ*:‘QÓi‡k‰D¿·ÿ¾Õ}òƒíîív÷Ãİ7îGøçğéÂùl¡\'²wíS]ìR$İ½ÿ\0Bnª*'),(100,100,700,500,'xÚíÏ¿nAÇññÙqBŠD\r	MèîÀRšDº4l|«óF÷½±s¦@â1((é¨(Qúô<T<\0w>[ˆ‚<Áw¥Ï®VšÙßì—Ÿ²×9(ÆW®Qoã¢J]¶Ïl}lÕ¿>—a\"_ÊÃ,TË25ví‚Jtybdš÷—Feß\\Û•M–êóÄøFgF&ÁÍUåĞtÉ‘l2’ÌU…Ó°N.ºŠY[/CÿÚÙ·7G«_?G\"m-ò|¿>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00h‚<¾¶+/Õçñ‹ìÚøFÛßŸ|¼³ŸÆ2:‘Iãß¹¶îÊåfÒïm×õ´t\Z_¹F½‹*uyœ¹ªp\ZÖñ…›«kÉØÈÔ–óET™®+º’MW²ëJ^V¾ÔYW\\Ÿùò­¼—fÈV9¼§ñUW1kën¦ƒÿÏ´yıïPÑ©ŒÚÎzø–Hôk{5}ò£íívÛñûş÷#üóøtá|¶ĞSÙ»ñ©.v)’îÎ?#¶©ó'),(100,100,700,600,'xÚíÏ¿nAÇññÙqA„‰\Z\ZÒİ=€+¤4‰.\n\nHiØøVç|Ø;wH´¼%%¢§ç1àØóÙBä	¾+}vµÒÌşf?ÿ’½ÆËai5¾¶:Uf—qaêøÜÔ\'FÍÉ‹Ö(’ñ•ìç¾Z•Yj:ëU¢«ÓT¦ËşÒ¨¤7fm’•ºe’ºFg©L¼«ÊQ\Z2’!#Ùd$¹­\n«¾K.CÅ¬­W¾íüë«\'ëŸ_>E\"m-r|‚/\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\Z/nÌÚÄ+uËø©÷¦K]£í»?|7Ç2:•IãŞØ¶år;é÷6t=*­Æ×¶Qgâ¢Êì2ÎmUXõ]|iç*Ã\ZE2NejÊù¢ò*ÓĞ•]É¦+Ùu%Ï*Wê,WŞå®|-oå^:d«İÑø<TÌÚ:Ìtøÿ™6¯ÿ*:“QtÃ·D¢ßÛó}Ó\'ßßŞ¾mÏ—wÿ¸áŸÇ§ëò…ÉŞ­Ët±K‘lwş\Zè©¼'),(100,100,700,700,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHh ³à*¤4‰6\n\néÒ°9¯|ùë¹‹‰Ç  LGE‰ÒÓóHğØg,DAà»ÒgW+ÍìoöóOÙk‚”Nã+×¨·qQ¥.[Ç§¶>²j.ÎdX“H¦—ò0ÕºLİº ]™çı¥QÙ7×vc“µú<1¾Ñ…‘YpKU94]F2d$»Œ$sUá4l“ó®bÑÖëĞ¿vúõÍóÍ/·‘H[‹¼Øï‚Ï\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0š ¯íÆÆkõyü2»5¾ÑöÃ÷\'¿ÙOS™Ë¬ñï\\[wår3ë÷¶ëzZ:¯\\£ŞÆE•º<Î\\U8\rÛøÜ-U†5‰djdnËåª\n*ÏL×•]É®+»’W•/uÑWÁg¾|+ïå²Uïi|İU,Úº›éàÿ3í^ÿ;Tt\"“¶³¾%ı\ZÏ¦O~ôçv7÷ÿ¸áŸÇç+ç³•ÈŞOu5¦H:¿bÈª„'),(100,100,700,800,'xÚíÏ¿nAÇññÙqBŠD\r1\rtwà\n)M¢‚]\Z6¾Õy£ûÇŞØ9S ñ”tT”(}z	;_,DAà»ÒçV·šÙßì×_rĞ9*Æ—®Qoã¢J]¶Ïl}lÕ¿9—a\"_ÈÃ,Të25vë‚Jtqbdš÷?Ê¡¹²›¬Õç‰ñÎL‚[ªÊÌtÉ‘ì2’ÌU…Ó°M]Å¼­×¡¿íìûÛç›Ÿß¾D\"m-òâ°>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00h‚<¾²¯ÕçñËìÖøFÛ?|ºµŸÇ2:‘Iãß»¶îÊåzÒÛ®ëié4¾tzUêò8sUá4lã…[ªkÉØÈÔ–ËUT™®+º’]W²ïJ^U¾ÔyW\\Ÿùò|fÈV™İÓøº«˜·u7ÓÑÿgÚİşw¨èTFmg;<K$ú=ìãEÓ\'?º;½¹;İÿâ~„.Ÿ®œÏVz*×>ÕÕ>EÒışóS©N'),(100,100,700,900,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHh [?ÀUHi9\n\ntiØœW¾üõÜÅ¦@Bâ%((é¨((==Ï€}Î	Q\'ø®ôñÊ«™ıÍ~ú%{MƒÒ©¹tzkŠ*u¹)lmNm}dÕ½8“qM\"™^Èİ,Të2Mlç‚JtqœÈ<~\Z•ıäÊnl¼VŸÇ‰ot‘È,¸¥ª&}F<fÄÛŒ8sUá4tñy_±hëun;ıúòñæçç‘H[‹<ÙïƒÏ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0Œš ÷¯ìÆšµúÜ<\rÁv‰o´}ûãÁûïöÃT&Ç2kük×Ö}¹\\Ï†oÛw=,šK×¨·¦¨R—›ÌU…ÓĞ™s·T×$’i\"s[.WUPy”ô]ñØo»â]Wü¬ò¥.úâ*øÌ—¯äÜIÆl•Ã[\ZŸ÷‹¶îg:øÿLÛÛÿÈ¤íuã³D¢ßã>ıÒÉ÷nN¿İœ¾»ıÅÃÿ\\>_9Ÿ­ôDö®}ª«]Š¤»ı;Bª'),(100,100,800,0,'xÚíÏ¿Ó@Çñ‰“KB:‰\Z.4Ğ­%ÚTH×ÜÉ§Cî\Zöâ•³\'ÿc=É9H<Å•tT”¼\0=Ä;`Ç‰÷ß•>¶¼Ùßì×ßrÔ9)šk×¨·¦¨R—›ÂÖæÂÖ§VíéÛKÖ(’ñ•<ÌBµ.ÓÄn]P‰®Î™æıG£rœÜØ×êó8ñÎ™·T•YÒeÄCF¼Ëˆ3WNÃ6^tó¶^‡ş´‹ïïo~}û‰´µÈ‹ã.ø\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0À 	òøÆn¬Y«ÏÍ«ì6ñ¶Ÿ~>ùüÃŞet&“Æpmİ•Ëí¤¶]×ÓÒ©¹vzkŠ*u¹É\\U8\r[³pK•a\"\'2µårU•gI×]ñ®+>tÅ¯+_ê¼+®‚Ï|ù^>ÊƒdÈV™İÓø¦«˜·u7ÓÉÿgÚşw¨è\\Fmg;\\Kd¼Øÿ|ÙôÉö»³ı®Üã~„Ÿ®œÏVz.G·>ÕÕ!EÒÃûa§Ò'),(100,100,800,100,'xÚíÏ?nÔ@Çñ·²)5di ³°RšD‚º4LÖ#ïDşÇøíÆ¦@âH)é¨(¹\0=Ç@âŒ×»Bäß‘>¶<~o~o¾ş–£ÆËIi5º¶:Ujó¨0utaêS£æôÍ¥k4–É•<Ì|µ)ÓÄtÖ«Œ¯Î™åıG£rœÜ˜­‰7êò8q.™z»R•y2â!#ŞeÄ™­\n«¾‹—¡bÑÖßŸvñıíóí¯o_Æ\"m-òâ8_\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\04^ß˜­‰6êòè¥÷¦K\\£íÇŸO>ÿ0wÉ´qïm[‡r¹öÏ6t=-­F×¶Qg¢¢Jme¶*¬ú.ZÚ•Ê°Fc™$23åj]y•gIèŠ‡®x×ºâW•+uŠ+ï2W¾“ò ²Uæ÷4¾‹¶3ü¦İé‡\ZŸË¨\rºáZ\"“åşç§¦O~´ßïwÓûoÜğÏá³µuÙZÏåèÖ¥º>¤Hzxÿ¨ï¨š'),(100,100,800,200,'xÚíÏ?nÔ@Çñ·²)5di ³°RšD‚º4LÖ#ïDşÇøíÆ¦@â”tT”\\\0jÄ¨¯w…(È	¾#}lyüŞüŞ|ş%G—“Òjtmu&*ªÔæQaêèÂÔ§FÍé«KÖh,“+¹ŸùjS¦‰é¬W_%2ËûFå8¹1[oÔåqâ\Z]$2õv¥*ó$dÄCF¼Ëˆ3[V}/CÅ¢­7¾?íâëë§ÛŸ_>EÚZäÙq¾\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00h¼<¼1[mÔåÑsïM—¸FÛ÷?}øf>Ndt&ÓÆ½µmÊåvÚ?ÛĞõ¸´\Z]ÛF‰Š*µy”Ùª°ê»hiW*Ã\Ze’ÈÌ”«uåU$¡+ºâ]W|èŠ_T®ÔE(®¼Ë\\ùFŞÉ½dÈV™ßÑø2T,Ú:Ìtòÿ™v§ÿj|.£6è†k‰L–ûŸ¿›>ùÁ~w¾ßı~÷ûş9|¶¶.[ë¹İºT×‡Iï?ğÏ©b'),(100,100,800,300,'xÚíÏ?Ó@Çñ\'›„´5lh Aã¤BÚfW^-\nt+!fã‘3+ÿcü’u(8%% çHÜ;N„(Ø|GúØòø½ù½ùú[š \'¥Ssí\ZõÖUêrSØÚ\\ØúÔª=}})Ã\ZE2¾’ûY¨ÖešØ­*ÑÕY\"Ó¼ÿhT“»±ñZ}\'¾Ñy\"“à–ª2KºŒxÈˆwqæªÂiØÆ‹®bŞÖëĞŸvñıíÓÍ¯o_\"‘¶yvÜ_\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\04AŞØ5kõ¹y‚İ&¾ÑöãÏGŸ~ØÏcÉ¤ñï][wår;éŸm×õ¸tj®]£Şš¢J]n2WNÃÖ,ÜReX£HÆ‰Lm¹\\UAåIÒuÅCW¼ëŠ]ñËÊ—:ïŠ«à3_¾“r/²Ufw4¾ê*æmİÍtòÿ™v§ÿ*:—QÛÙ×/öÿŞ4}òƒıîl¿ûüî÷#üsøtå|¶Òs9ºõ©®)’Ş\0i¨,'),(100,100,800,400,'xÚíÏ½nÔ@†á³?Ù„‰\Z²4ĞÙ°RšD‚º4LÖ#ïDşc|vcS q´”tT””4ô\\÷Àx½+DA®àé±åñ9óùò[\Z/\'¥ÕèÚ6êLTT©Í£ÂÔÑ…©OšÓW—2¬ÑX&Wr?óÕ¦LÓY¯2¾:Kd–÷Êqrc¶&Ş¨ËãÄ5ºHdêíJUæIÈˆ‡Œx—g¶*¬ú.^†ŠE[o|ÚÅ·×O·¿¾~‹´µÈ³ã|	\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Ğxyxc¶&Ú¨Ë£çŞ›.q¶ï>úøÃ|šÈèL¦{kÛ:”Ëí´¶¡ëqi5º¶:Ujó(³UaÕwÑÒ®T†5\ZË$‘™)WëÊ«<IBW<tÅ»®øĞ¿¨\\©‹P\\y—¹ò¼“{É­2¿£ñe¨X´u˜éäÿ3íNÿ;Ôø\\FmĞ\r×™,÷ÿ¾7}òƒıî|¿ûáî÷#üsølm]¶Ös9ºu©®)’Ş\0ÉI¨ô'),(100,100,800,500,'xÚíÏ1nÔ@Æñ·ŞÍ ¤HÔ¥	}€­Ò$š(h¡KÃd=òNäµÍøíÆ¦@â”tT”\\€c Ñq\0ìuV(Er‚ÿH?Æzo¾7ßşÈAä¨p\Z_¹Z½×eêòxm«øÜV\'VíÉÛÖ(’ñ¥<ÎB¹)Rc[T¢ËS#Ó¼?Ô*‡æÚnm²QŸ\'Æ×:72	n©*3Óe$CF²ËH2W®†6Ytó¦Ú„ş¶óï·¿¿DšJäåa|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Pyzm·6Ş¨ÏãW!ØÖøZ›O¿}şi¿Œet*“ÚpMÕ•ËÍ¤ÿ6]×óÂi|åjõ6^—©ËãÌ•k§¡n©2¬Q$c#S[,WePyaº®dèJv]É¾+y]úBç]q|æ‹÷òQ™![eö@ã›®bŞTİLG÷Ï´»ıÿPÑ™ŒšN;<Kd¼öè¸î“ŸÜşİvü}øÅıw.Ÿ®œÏVz&7>ÕÕ>EÒışª‘¨½'),(100,100,800,600,'xÚíÏ?nAÇñçµã„‰\Zb\Zèvà\n)M¢‚RšL¼£õDûÙggMDË\r((é¨(¹\0=Ç@ÊØõÆBäß‘>;ÚÑ{ó{óíVš G¥ÓøÊ5êm\\T©ËãÂÖñ™­­Úã7ç2¬Q$ãy˜…j]¦‰İº ]œ$2ÍûŸFå0¹¶kÖês“øFç‰L‚[ªÊ,é2Ìav&sUá4lÍ¢«˜·õ:ô·ı¸|¾ùıık$ÒÖ\"/»às\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0ƒ&Èãk»±ñZ}¿Ánßhûñ×“Ï?í—±ŒNdÒø÷®­»r¹™ôß¶ëzZ:¯\\£ŞÆE•º<Î\\U8\rÛxá–*Ã\ZE2NdjËåª\n*Ï’®Ë]f×eö]æUåKwÅUğ™/ßÉyÙ*³{\Z_wó¶îf:úÿL»ÛÿÊ¨íl‡g‰ŒÃ}júäGw§³»Ó·÷¿¸áŸË§+ç³•ÊÁOuµO‘t¿ÿ¡Ã¨†'),(100,100,800,700,'xÚíÏ¿nAÇññÙqBŠD\r1\rtwà\n)M¢‚]\Z6¾Õy£ûÇŞØ9S ñ”tT”(}z	;_,DAà»ÒçV·šÙßì×_rĞ9*Æ—®Qoã¢J]¶Ïl}lÕ¿9—a\"_ÈÃ,Të25vë‚Jtqbdš÷?Ê¡¹²›¬Õç‰ñÎL‚[ªÊÌtÉ‘ì2’ÌU…Ó°M]Å¼­×¡¿íìûÛç›Ÿß¾D\"m-òâ°>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00h‚<¾²¯ÕçñËìÖøFÛ?|ºµŸÇ2:‘Iãß»¶îÊåzÒÛ®ëié4¾tzUêò8sUá4lã…[ªkÉØÈÔ–ËUT™®+º’]W²ïJ^U¾ÔyW\\Ÿùò|fÈV™İÓøº«˜·u7ÓÑÿgÚİşw¨èTFmg;<Kd¼öèwÓ\'?º;İŞÜÿâ~„.Ÿ®œÏVz*×>ÕÕ>EÒışé£©N'),(100,100,800,800,'xÚíÏ?nÔ@Çñ·²)5di ³°RšD-ti˜¬GŞ‰üñÛMÄ1((ÓQQrzÄ°×Xˆ‚œà;ÒgF#½7¿7_~ÉQä¤p\Z]»Z½ò2qY”Û*º°Õ©U{úæR†5™ÊìJ¦¡Ü‰±­*Ó«3#‹¬¿Ô*ÇæÆîm¼SŸÅÆ×º22n£*KÓeÄCF|ÈˆSWæNC¯»ŠUSíBÿÚÅ··Ï÷?¿ŞMEšJäÅq|	\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Py|c÷6Ú©Ï¢—!ØÖøZ›?|ún?Ïdr&óÚ¿wMÕ•Ëí¼ß›®ëiá4ºvµzåeâ²(ueî4´ÑÚmT†5™ÊÌÈÂ›mT™®+ºâCW<vÅ¯J_èª+.ƒO}ñN>È3d«,ïi|İU¬šª›éäÿ3^ÿ;Ôô\\&M§¾%2[gİ\'?ús[çı?îGøçñÅÖùt«çrtëİ)’Œçoz=¨'),(100,100,800,900,'xÚíÏ?nAÇñç?qBŠD\r1\rt»p…”&ÑFA†.\rïh=ÑşcöÙÙ¥@Bâ”tT\\€c qf½¶9Áw¤ÏŒFzo~o¾ü–£ÆËIi5º¶:Ujó¨0utaêS£æôÕ¥k4–É•ÜÏ|µ)ÓÄtÖ«Œ¯Î™åı¥Q9NnÌÖÄuyœ¸F‰L½]©Ê<	ñï2âÌV…UßÅËP±hëï_»øşúéö××Ïc‘¶yv‚/\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\Z/oÌÖDuyôÜ{Ó%®ÑöıÏG˜OÉ´qom[‡r¹ö{º—V£kÛ¨3QQ¥62[V}-íJeX£±L™™rµ®¼Ê“$tÅCW¼ëŠ]ñ‹Ê•ºÅ•w™+ßÈ;¹—Ù*ó;\Z_†ŠE[‡™Nş?Óîõ¿CÏeÔİğ-‘Ér~kúäûÛ|~¸ûÇıÿ<>[[—­õ\\n]ªëCŠ¤‡óÂ¨à'),(100,100,900,0,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHh ³%Ú«Ò$Ú((Ğ¥Éæ¼òmä¬ç.>\n$$^\"%/@ÏcDâ°ÏwBä	¾+}ly=³¿Ù¯¿e¯	rP:¯\\£ŞÆE•º<.lŸÚúÈª=z{&Ã\ZE2¾‡Y¨–ejìÚ•èâØÈ4ï?\Z•}smW6YªÏã™7W•CÓe$CF²ÉH2WNÃ:9ï*fm½ıi§?.Ÿ¯î¾}‰DÚZäÅ~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Ğy|mW6^ªÏãW!Øµñ¶=¹ıi?et,“Æ¿wmİ•ËÍ¤¶]×ÓÒi|å\Zõ6.ªÔåqæªÂiXÇçn®2¬Q$c#S[ÎUPyfº®dèJ6]É®+y]ùRg]q|æËwòA˜![åğÆ7]Å¬­»™ş?Óæô¿CE\'2j;ëáZ\"ãïÛŸ/›>ùÑv÷ÓvWî¿q?Â?‡OÎg=‘½Ÿêb—\"éîı¯/¨š'),(100,100,900,100,'xÚíÏ?nÔ@Çñ·²)5$4ĞÙØ\n)M¢‰‚Bº4LÖ#ïDşÇøíÆ¦@BâH)(é¨RPpzÄ°×»Bäß‘>¶<~o~o¾ş–½:ÈAá4ºrµzåeâ²(·Utj«#«öèâL†5\ZËäR¦¡\\‰±­*ãËc#³¬ÿ¨UöÍµ]Ûx¥>‹¯und\ZÜBUM—ñ&#N]™;\rm|ŞUÌ›júÓN¿¿y¾şu÷e,ÒT\"/ö»à3\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0ƒ:Èãk»¶ÑJ}½Á¶Æ×Ú|øùäö‡ı<‘Ñ±LkÿÎ5UW.7ÓşÙt]O§Ñ•«ÕÛ(/—E©+s§¡ÎİBeX£±LŒÌl±X–Aå™éºâ¡+ŞtÅ»®øUéwÅeğ©/ŞÊ{y`†l•Ã{\Z_wó¦êf:øÿL›Óÿ5>‘QÓi‡k‰L¾m~ªûäGÛİÛİäş÷#üsølé|ºÔÙ»ñ‰.w)’ìŞ\0÷©b'),(100,100,900,200,'xÚíÏ?nÔ@Çñ·²)5$4Ğ°RšD‚]\Z&ë‘w\"ÿcüvcS !q	\nJ:*Š¹\0Ô‰Pa¯w…(È	¾#}lyüŞüŞ|ù%{uƒÂ©¹tµzkò2q™ÉmeNmudÕ½:“aÆ2¹ûi(WEÛÖ•ñÅq,³¬ÿ¨Uöã+»¶ÑJ}Å¾Öy,ÓàªrwÑm2¢Ô•¹ÓĞFç]Å¼©V¡?íôöõÓõÏ¯ŸÇ\"M%òl¿>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00¨ƒ<¼²kkVê3ó<ÛÆ¾ÖæıG¿ÙOË´öo]Suår=íŸM×õ¸pj.]­Şš¼L\\fRWæNCkÎİBeX£±Lb™Ùb±,ƒÊ“¸ëŠ†®hÓíº¢¥/tŞ—Á§¾x#ïä^<d«ŞÑø²«˜7U7ÓÁÿgÚœşw¨ñ‰ŒšN;\\Kdr³ıù»î“lw?lw¿ß}ã~„Ÿ-O—z\"{×>Ñå.E’İû>şª*'),(100,100,900,300,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔĞ@ƒìl…”&ÑDA!]$Äd=ÚÈÿ¿İØHH\\‚‚’* çHÜ{½+DANğécËã÷æ÷æëoÙ«ƒNã+W«·q^¦.‹s[Å§¶:²j.ÎdX£HÆ—rÊU‘\ZÛº ]™fıG­²o®íÚ&+õYb|­3#“àæªrhºŒdÈH6ÉÂ•¹ÓĞ&ç]Å¬©V¡?íôû›§ë_·_\"‘¦y¶ßŸ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0ÔA^ÛµWê³øE¶5¾ÖæÃÏGŸ~ØÏcË¤öï\\Suår3éŸM×õ¸p\Z_¹Z½ó2uY¼peî4´ñ¹›«kÉØÈÔóeT˜®+º’MW²ëJ^–¾ĞYW\\¿ğÅ[y/÷Ì­rxGã«®bÖTİLÿŸisúß¡¢5v¸–ÈøÛößëºO~°İı¸İ}~÷ûş9|ºt~±ÔÙ»ñ©.w)’îŞ\0Ï‰¨ô'),(100,100,900,400,'xÚíÏ½nÔ@†á³?Ù„‰\Z\ZèÆ°RšD‚]\Z&ë‘w\"ÿ1>»±)¸\0Z\nJ:ª)(iè¹$î{½+DA®àé±åñ9óùú[öê …SséjõÖäeâ2“ÛÊœÚêÈª=zu&Ã\Zer!÷ÓP®Š$¶­*ã‹ãXfYÿQ«ìÇWvm£•ú,Š}­óX¦Á-Tå0î2¢!#ÚdD©+s§¡Î»ŠyS­BÚé·×O×¿n¾ŒEšJäÙ~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Pyxe×Ö¬Ôgæy¶}­ÍûŸ>ı°Ÿ\'2:–iíßº¦êÊåzÚ?›®ëqáÔ\\ºZ½5y™¸Ì¤®Ì†Öœ»…Ê°Fc™Ä2³ÅbY•\'q×\r]Ñ¦+ÚuE/J_è¼+.ƒO}ñFŞÉ½xÈV9¼£ñeW1oªn¦ƒÿÏ´9ıïPã5v¸–Èävûï{İ\'?Øî~Øî~¼ûÆıÿ>[:Ÿ.õDö®}¢Ë]Š$»÷x©¼'),(100,100,900,500,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔĞ„Î>ÀVHiMèÒ0Y¼ùã·›	‰KPPÒQQPpzDÇ°×Y!\nr‚ïHÆzo~o>ÿ’½&ÈAé4¾rzUêò¸°u|fëc«öøå¹ŒkÉôRîg¡Z—©±*Ñå‰‘y>\Z•}sm76Y«Ïã]™·T•CÓg$cF²ÍH2WNC—\\ô‹¶^‡á¶³o¯6?¿|ŠDÚZäé~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Ôyxm76^«Ïãg!ØÎøFÛw?}øn?Ner\"³Æ¿qmİ—ËÍlø¶}×ãÒi|å\Zõ6.ªÔåqæªÂièâ·T×$’©‘¹-—«*¨<1}W2v%Û®d×•<¯|©‹¾¸\n>óåky+÷Ì˜­rxGã‹¾bÑÖıLÿŸi{ûß¡¢S™´½n|–Èôë¸GGÍüàöïûÛßw¿xáŸËç+ç³•ÊŞOuµK‘t·ÿø±©…'),(100,100,900,600,'xÚíÏ?nÔ@Çñ·ŞÍ ¤HÔĞ@ç9ÀVHi9\n\nHi˜¬GŞ‰üñÛM„DÅ\r((é¨((¸\0=Ç@âØë¬9Áw¤G½7¿7_~Ë^ä t\Z_ºF½‹*uy\\Ø:>µõ‘U{ôâLÆ5‰dz!w³P­Ë4±*ÑÅq\"ó|øiTö“+»±f­>7‰ot‘È,¸¥ª&}†3Ì6Ãd®*œ†Îœ÷‹¶^‡á¶Óï¯o~}ı‰´µÈ“ı>ø\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0À¨	rÿÊnl¼VŸÇOC°]âmßı|ğñ‡ı4•É±Ì\ZÿÆµu_.×³áÛö]K§ñ¥kÔÛ¸¨R—Ç™«\n§¡‹ÏİRe\\“H¦‰Ìm¹\\UAåQÒw™±Ël»Ì®Ë<«|©‹¾¸\n>óåky+w’1[åğ–Æç}Å¢­û™ş?Óöö¿CE\'2i{İø,‘é·q>4Cò½›Ó÷7§/oñ0Â?—ÏWÎg+=‘½kŸêj—\"énÿïã©N'),(100,100,900,700,'xÚíÏ¿nÔ@Çñ9ßå\nR$jHh [?ÀUHi9\n\ntiØœW¾üõÜÅ¦@Bâ%((é¨((==Ï€}Î	Q\'ø®ôñÊ«™ıÍ~ú%{MƒÒ©¹tzkŠ*u¹)lmNm}dÕ½8“qM\"™^Èİ,Të2Mlç‚JtqœÈ<~\Z•ıäÊnl¼VŸÇ‰ot‘È,¸¥ª&}F<fÄÛŒ8sUá4tñy_±hëun;ıúòñæçç‘H[‹<ÙïƒÏ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0Œš ÷¯ìÆšµúÜ<\rÁv‰o´}ûãÁûïöÃT&Ç2kük×Ö}¹\\Ï†oÛw=,šK×¨·¦¨R—›ÌU…ÓĞ™s·T×$’i\"s[.WUPy”ô]ñØo»â]Wü¬ò¥.úâ*øÌ—¯äÜIÆl•Ã[\ZŸ÷‹¶îg:øÿLÛÛÿÈ¤íuã³D¦_Æ=úİÉ÷nNßİœ~»ıÅÃÿ\\>_9Ÿ­ôDö®}ª«]Š¤»ı7Òª'),(100,100,900,800,'xÚíÏ?nAÇñç?qBŠD\r1\rt»p…”&ÑFA†.\rïh=ÑşcöÙÙ¥@Bâ”tT\\€c qf½¶9Áw¤ÏŒFzo~o¾ü–£ÆËIi5º¶:Ujó¨0utaêS£æôÕ¥k4–É•ÜÏ|µ)ÓÄtÖ«Œ¯Î™åı¥Q9NnÌÖÄuyœ¸F‰L½]©Ê<	ñï2âÌV…UßÅËP±hëï_»øşúéö××Ïc‘¶yv‚/\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\Z/oÌÖDuyôÜ{Ó%®ÑöıÏG˜OÉ´qom[‡r¹ö{º—V£kÛ¨3QQ¥62[V}-íJeX£±L™™rµ®¼Ê“$tÅCW¼ëŠ]ñ‹Ê•ºÅ•w™+ßÈ;¹—Ù*ó;\Z_†ŠE[‡™Nş?Óîõ¿CÏeÔİğ-‘É·ı¹lúäûÛ‡ı9¿ûÇıÿ<>[[—­õ\\n]ªëCŠ¤‡óÈ]¨à'),(100,100,900,900,'xÚíÏ?nÔ@Çñ·²)5$4ĞÙØ\n)M¢‰‚Bº4LÖ#ïDşÇøíÆ¦@Bâ)é¨(RpzÄ°×Xˆ‚œà;ÒgF#½7¿7_~É^ä p\Z]¹Z½ò2qY”Û*:µÕ‘U{tq&ÃšLev)ÓPnŠÄØÖ•éå±‘EÖ_j•}sm·6Ş¨Ïbãk]\Z™·R•CÓeÄCF¼ËˆSWæNCŸwË¦Ú„şµÓooo~ı<i*‘û]ğ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0€AäñµİÚh£>‹^†`[ãkm>üxòé»½ÉäXæµçšª+—›y¿7]×ÓÂitåjõ6ÊËÄeQêÊÜih£s·RÖd*3#[¬ÖePyfº®xèŠw]ñØ¿*}¡Ë®¸>õÅ[y/Ì­rxOãë®bÙTİLÿŸi÷úß¡¦\'2i:íğ-‘ÙİxÖ}ò£?·ãyÿûşy|±v>]ë‰ìİøD×cŠ$ãùL©¨');
/*!40000 ALTER TABLE `map_data` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `map_parameters`
--

LOCK TABLES `map_parameters` WRITE;
/*!40000 ALTER TABLE `map_parameters` DISABLE KEYS */;
/*!40000 ALTER TABLE `map_parameters` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `parties`
--

LOCK TABLES `parties` WRITE;
/*!40000 ALTER TABLE `parties` DISABLE KEYS */;
/*!40000 ALTER TABLE `parties` ENABLE KEYS */;
UNLOCK TABLES;

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
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_8cesygf9vnunkwbaay3b79dso` (`master_id`),
  KEY `FKf2abbxo21c1dyko75xpf7tuk7` (`bestia_id`),
  KEY `FK2lj7purwyjg5mbgf0wh3v6ofg` (`account_id`),
  CONSTRAINT `FK2lj7purwyjg5mbgf0wh3v6ofg` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`),
  CONSTRAINT `FKf2abbxo21c1dyko75xpf7tuk7` FOREIGN KEY (`bestia_id`) REFERENCES `bestias` (`id`),
  CONSTRAINT `FKsm6bf38aqgyu3ah6vtb7s5sh8` FOREIGN KEY (`master_id`) REFERENCES `accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `player_bestias`
--

LOCK TABLES `player_bestias` WRITE;
/*!40000 ALTER TABLE `player_bestias` DISABLE KEYS */;
INSERT INTO `player_bestias` VALUES (1,35,30,4,15,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,'Dixfax',6,6,1,2,1,3,30,35,NULL),(2,7,14,10,10,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,1,'Penis',5,5,2,2,2,0,0,0,NULL);
/*!40000 ALTER TABLE `player_bestias` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `player_items`
--

LOCK TABLES `player_items` WRITE;
/*!40000 ALTER TABLE `player_items` DISABLE KEYS */;
INSERT INTO `player_items` VALUES (1,10,1,1);
/*!40000 ALTER TABLE `player_items` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `script_vars`
--

LOCK TABLES `script_vars` WRITE;
/*!40000 ALTER TABLE `script_vars` DISABLE KEYS */;
/*!40000 ALTER TABLE `script_vars` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `status_effect`
--

LOCK TABLES `status_effect` WRITE;
/*!40000 ALTER TABLE `status_effect` DISABLE KEYS */;
/*!40000 ALTER TABLE `status_effect` ENABLE KEYS */;
UNLOCK TABLES;

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

--
-- Dumping data for table `tileset`
--

LOCK TABLES `tileset` WRITE;
/*!40000 ALTER TABLE `tileset` DISABLE KEYS */;
INSERT INTO `tileset` VALUES (1,256,1,'mountain_landscape_23','{\"mingid\": 1, \"maxgid\": 256,\"name\": \"mountain_landscape_23\", \"size\": {\"width\": 16, \"height\": 16}}');
/*!40000 ALTER TABLE `tileset` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2018-03-25 13:22:13
