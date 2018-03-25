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
INSERT INTO `map_data` VALUES (100,100,0,0,'x��Ͽn�@��9��\n\"�HԐ�@gK�W!�I�ҥas^�6�?�s��ǠHIGE���1�x�g$�ߑ>^Y���엟��z9���W�Ug��l����Lsl�_���&�L/e?����R�[�]��2/��V� �6���I�Z]�2�v�*Gi�Hƌd���.��>9��Y�ᶳoo�m~|��t����\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`�zytm6&^�+�ޛ>u�v��nn�29�Y��ۮ	�r3�]�zRY��l���e��\"�m]Z�}|n�*cM\"��27�rU{��i�JƩd;�즒׵�t�k�rW��� �U��|:]v:��N���,�ʤ��Y�^��vH~(���/V����ʺ|���w�2]�N�v�/Y�F�'),(100,100,0,100,'x��Ͽn�@��9��\n\"�HԐ�@gK�W!�I���ds^�6�?�s���@JAIGE���Hy�gJA��;��+K3���z#;����j|a[u&.��qi���4�F���W2�$����^WYjz�U���T���Ӫ쥗fc���\"I]��Tf�.U� \rɘ�l3��֥U�\'��c�5k?�v�����׷/�H׈<���\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0F��G�fc⵺\"~��S�j�������T&G2k�{�5�]�f÷SO*��mՙ��3[Ĺ�K���O�Re�I$�T�Z�j��4\rS�8�l��۩�u�*]��ڻ�U��<H�l��{߄�Eׄ�������KE�2�~|V��O��P�Vv����\\>_Y���Xv�\\���)�����G~'),(100,100,0,200,'x��Ͽn�@��9��\n\"�HԐ�@gK�W!�I��Хas^�6�?�s��Ǡ�����@�c �T����<�w��W�f�7����^�+��mՙ��3[ĥi��\Z5��^�X�H�粛�z]e��W�ΏR��O���^��I��$u�.R�y�T��4d$cF��Hr[�V}����E׬�p����\'�_>E\"]#�t/�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�^\\���������O]����?|5�29�Y��ڮ	�r5�]�zTY�/l���e��\"�m]Z�}|j�*cM\"��27�rU{��i�JƩd;��L%/kW�\"4���z#��^:f��1x:]v���N���-ˤ��Y���9�C�}�]��~�­��+���Ε�t�7%�9�HF'),(100,100,0,300,'x��Ͽn�@���rR$jHh�A�D{R�D�@	]$���m���.>\n$����*%/@�c �����<�w��-�3����Kv:/�����v�LR5�-�ʴɉi����/e�(�ɹ��Y�yf6֫��G����S��.�ڤ+ue��N�L�]��A2�1#�f��m*�~����y߮�p�ɷ�O�?���\"}+�t/�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0u^\\��IV����f��N��?~�n>O$:�i��۾\r�r5�}�zT[M.l��$U��2)lSY����.TƊb�d23�b�x��Y�Jǩt;��L��\ZW�<47��~\'�^6f��1x:�}v���N���-K���Z��������˭���}�a�[�ϖ�K=��+���oJ~��\rzG'),(100,100,0,400,'x��Ͽn�@���rR$jHh��%ګ��$rJ�Ұ9��6�?�s��7�����JIIC�c �����<�w��-�3����KvZ/����¶�LRֹ-��4ɉi���W/d�(�ɹ�.}�����֫��G�̊�U��.�ƤkuE��V�L�]��A2�1#�f�K[�V}����y׬�p����O6?�?�\"]#�t/�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�^\\��I������\\���?~7�&ɴuomׄv���.L=��&�Ug���m�,m]Z�}rj*cE�L2��j�����,L��T��Jo�җ��t�kz#��^6f��1x:�]v���N���-K��x�P��������VE��­�g+�+=��+���oJ~��\r��G�'),(100,100,0,500,'x��Ͽn�@���rR$jHhBgK�W!�I���ds^�6�?�s�A�������1���\0��\n(y��H�,��o��o����V�K۩3I��L*�&��=2j�޽���X&�[�fU��X��g2+��Ne/�2k��ԕi�:�g2�v�*Y�Hǌt������Mz:�}���m��/׿�}�E�V��^�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00�<�2k��ԕ�+��&s���~>����L$:�i�>ؾ\r�r=�}�zV[M.m��$U��2)lSY����.TƊb�d23�b�x��Y�Jǩt;��M�o\ZW�<47��~/�Q6f�<0�6t��6�������[*>��6�B�����˽�n~�½�gK능��ε�u�7%�;�\0�FG�'),(100,100,0,600,'x��Ͽn�@��9��\n\"�HԐ�@gK�W!�I��P �as^�6�?�s��6o@A������1�x�g��<�w��W�f�7����^�+��mՙ��3[ĥi��\Z5�o^�X�H�粛�z]e��W�ΏR��O���^��I��$u�.R�y�T��4d$cF��Hr[�V}����E׬�p���w�6���D\"]#�|/�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�^]���������O]�ݧ��?�0_�29�Y�>خ	�r5�]�zRY�/l���e��\"�m]Z�}|j�*cM\"��27�rU{��i�JƩd;��N%�kW�\"4���z/�A:f��3x:]v���N���-ˤ��Y�^�Gt���NEo����+���Ε�t�7%�=�\0�xGj'),(100,100,0,700,'x���1o�@��/Nځ\n�3�,��k&�.�\\�.\\�S�*��}I$~CG6&F����@�߀���6M�O�{�+ҝ���O?e#�d\'��?����?N�v���?4�=��ޫH���},[�,�&���m��;�es���;�O��S��0�]7�Nf{��nX���rF0��غl�;��d�-�v����ُ�=�b\"�t��\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0+���ЊzD�P�zDBM�=���C�H�	=t����Cz�B=\"��&��#zhB]���ZЃMu���GS�C�붠=��&-�A����7mAz4�*-�A�������=��zܦ=�Qg�mAz�=�XGzУ��Ղ��#z����G���=�Q\'���.ZЃuq\'=�L����.�ϳ���8w���?|3�mi�K\'���bRn����(O=J��Ol�b�Ӿ���������9�V˓v(�&�\r����<T�����T�\"��-7�Y<��7�N��l\'�W|Y����N;�����/�H�(ͫ�*׳������˥�}���W���͡�Cw gq�\r�L�_<$A�>'),(100,100,0,800,'x���?o�@��\'��\n�34,��k&�.�\\�n]��\'��6�K�0 �2٘y�yH�.�\nt�C�<��,}U�s����\'�U�s�K]9��i��I4Uet��#����5W�M���l1��D-�uԾ8N�7Y�S9:H��\\�3g&qb*7H�k��9�\'���9#^�g��jg���1�˙]~��7��?�~n�%���>�����o1x�i���G��CN부<Bm��G��CN���<Bk���Gh�CN���<B\nrⰀ<B���!xȉ���<�!9nxȲ�<$�m\0xH��\02-���?<�!5����q�xH�{wxȷ������<$ǽ<v׃{kx�Cj�;�����Y�C^�[�c�<���<�ƽ-<�!9�m��<�Ž,�!9�����{�R�������.xȋ{Gxl������Ը��<$ǽ<���������߃{3x�Cj�{�����cyqo�0=�w�<�ƽ<�!9�౛��ް���7��_U�]���f�L��֪Eb*W�����ԡ�1u+�Nץ����˿��I�]t�+gT4-R=�2]L���h�G���զNB=��ƅu�4�O��S������Uar7�7�d&K��AҜ�σ������t��wZ}���j�P��-����GgX-O~Hw�N��_�|�;_�k���	�]�ԍ���~�sl��'),(100,100,0,900,'x���?o�@��_�4R%fhY`�%�LH]Z�**l]�&��Ub��:HH�	F6&�\0;/�����t���Ċt�;���O�)��|�.|R��Es��N���w��Ԛ��Z��v\'E��Ɖ[�\"X��(��l���\\����!��IZ�ab�B����#n���{���}(��Y=cX-���m\'__>Z����kV-����v��������������}�X�\\�����G�w�P�xhz�}*��ʵ��a�Z�Z�Z�Za�Z�Z�Z�Za�Za�Z�Z�Z�Za�Z�Z�Z�Za�Za�Z�Z�Z�Za�Z�Z�Z�Za�Za�Z�Z�Z�Za�Z�Z�Z�Za�Za�Z�Z�Z�Za�Z�AD�Dea�.��Eː΢�E��IZ�����￹=�Y�L_�jQO���淪W=�|�.|R��E��}(�љkF�k��.M�\"�ä^7����zU�,O�0�\'�E:I�W���$���nY���1�����~�����{l��n�|V=�4�ޗr��]�1z�n���n�|0��d\Z�m�*���]���_���'),(100,100,100,0,'x��Ͽn�@��9��\n\"�HԐ�@gK�W!�I��Ұ9�|�빋M��K �������y$ށ��� O�]�㕥���~�%{����j|i[u&.��qi���4�F���2�I$���}�����֫D\'�̋�U9H���$kuE��V�̼]��Q\Z2�1#�f$��K��O�CŢk�~�v��͓�ϯ�#��yz��\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�Z/����kuE��{ӧ���Ï�7�ͧ�LNdֺw�kB�\\φo�UV�K۪3qYg��s[�V}�ۥ�x&�LS��j�����4t%cW��Jn�����t�k�rW���r/�U��h|*]v:��N����Ne����p>��g�|���n����\n�����Wz*{�.�՟��y�o��G~'),(100,100,100,100,'x��Ͽn�@��9��\n\"�HԐ�@g?�UHi9�4l�+�F��z�bS �H�tT�y��<����Q�\'��������f������ae5���:�uf��4M|f�c����K�$����^WYjz�U���T���Ӫ�Wfc���\"I]��Tf�.U�(\rɘ�l3��֥U�\'�b�5k?L;�y�l����H�kD���}\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0��ˣ+�1�Z]�����k������[�e*�����P.׳�ۅ�\'���Ҷ�L\\֙-��֥U���v�2�I$�T�Z�j��4\r]�ؕl��]W�v�.Bq�]�w�A�c���=��CŢk�N���i;��RѩL���Χ�����e���+�3|��._��]�LWw)w�~��HF'),(100,100,100,200,'x��Ͽn�@��9��\n\"�HԐ�@g?�UHi9�4l�+�F��z�bS �H�tT��\0�</@��|Q�\'��������f?�����ae5���:�uf��4M|f�c����S�$����^WYjz�U���T���Ӫ�Wfc���\"I]��Tf�.U�(\rɘ�l3��֥U�\'�b�5k?L;������珑H׈<>��\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0F��{Wfc⵺\"~��S�j�����_͇�LNdֺ׶kB�\\φo�TV�K۪3qYg��s[�V}�ۥ�x&�LS��j�����4t%cW��Jn��g��t�k�rW��7r\'�U�ni|*]v:��N����Ne����p���_�|w����o��xX���u�JOe��e���r3/�\r7�I'),(100,100,100,300,'x���?n�@����DH��!����\n)M\"G�@	1Y������K QP�QQrz����7 \nr��H[�7�7_~�^�尲\Z_�V���:�E\\�&>3ͱQs��k��B�s_��,5��*��I*�b�hU�+�1�Z]����E*3o��r���d�H�In�Ҫ��P�蚵N;�������ϑH׈<>��\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0F��{Wfc⵺\"~��S�j������ͧ�LNdֺ��kB�\\φg�TV�K۪3qYg��s[�V}�ۥʸ&�LS��j�����4t%cW��Jn��絫t�k�rW��wr\'�U�ni|*]f:��L����ʤ��Za}��{��ww��n���7F����ʺ|���w�2]�I�n޿�>G�'),(100,100,100,400,'x��ѿn�@��?IA��\Z\Z���\n)M\"GF�.\r���n�;۬�.>\n$ހ\n)%%%\r=���;�w��\"�J|W�ز��3����i��V�ۨ3ɼ*�,��:95��Qs�<�n��28����e����*���Lvg�Fe?�4K�.����5:�d��XU��#�z����Vs�~�����^��i��_<Z~���/��\"��C�=\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0���f\0y�(\'���G\\�#��GL�#��GL�#.��\\�#&��x�wi�&Y��%O�7��5ھ�v���y?�ޱ�ʶu�.W���\rUJ�Ʌmԙd^v�Ll5��Wə�t�חA&��O+��0UiW�n�қ��i�J�͕wW���r\'�z��R�,��u����3mN�=T�Dzm��~+�w�o_�u�۷������x=���N��L�Dv�\\��_]���O�4R0'),(100,100,100,500,'x��ѽn�P��/��@�T�Z����	�K+WA��ns��J�`��7��Б���`�2�ظ\0��1��\'��l���X�||N���OYI�\Z���F�7�;��\r���Cn�jKq5��t k�$;A81����N �����zp����F}?�R�\nd91G��f���{��{�]�M&�~�D+��������:���SS$�<[�7^#\'������\Z��COm�CSx�-xh\n]ᡧ�Y��G]��x�5<�tS<�cx��6x�Q�nk�u\n=��<��]-���ᡧ�X��G���xT9<�4<�b����*���fi�U=��<��<,���*ᡧyY��Gٛ�x�=<�4o<�(k��������e�ek�x�Q���Ӣ-���,��������\\Y��G�CO.-��Cs�-��Cs�-��e�\Zsm��sm��N<���x���L��C��r=,�p=s<�К�yぇ�\\���X�/�3�͹���p=c<�К��⁇�\\��V��<:OBol���<I�I�6{����o�ْ4vd9�ޚl�?.����,_�dh�whR�� �5���d��#+��h�R ���\'V��*�X埯�/W�/�hh[��qu��y\'�bo+��,|�?��F��6�>������+�,oR����Pܚ[�t��v.V���O����W{&��쮬�F���K����&��'),(100,100,100,600,'x��ѱn�P��/N��\n�3�,���	�K+WE����Ur��6�7�À��Tb`dcb��y$�\'nAt�N��ߨ���ߗ_�Sy��m�/l���Ef\'�Ԕ�)M0�/O�}u\"�����<K��� ��Q*���*�^zi�&�7IRW�A*=o�!�Aڜ��g$�3��-�6�Er�\\1�˙_~�ɷ�O�?�~�D�R��^s��w��ioF����ޕ�@N{Wz��=��ގ�@N{?zl������r�[�c�,聗����r�[�c�<���=P�ގ�@N{;zl�=��ޕ�@N{[z����^��j�[у�ioE��^�;�c�<�7�=P�އ�@N{zl�=��ޜ�@N{wz�{hoBz�zh����r��ӂ�iЂ�i;��C{��-�A�h�=��V���X�+z`E��=��V���X�+z`E��-��V���X�+z`E��-��V���X�+Z`E��-��V���X�+Z`E��T������x�$~�Y��\n��?~7���9�^��ںl.����o���(�!��Up&����#[Lm�������N$�T�&��q�ܕ�w%���ۻ���à���n��7�N���A��EsŠ.�g���3����CE�ҩ���j^��[��Z�|������Ww���#�����u�q8��+���S���ߎ���'),(100,100,100,700,'x��ѱn�@��/N��\n�3�,���	�K+WE�[��)�*����:H�CG6&Ɗ���@�g�� ���\rK�X�������]�\Z/��\r�m�3�*�,��:=2��	f��X���H�L�\'�Z�EnV�I�r��� ;��Y�l�,�]F��� {y��u3�͌lb��\r~�����^��ێ�_>Y~��!ik��;q��?:�E]h�=t��.�Ѕ��Bz�B]h�=t��.�Ѕ��Bz�B]�=t��.�Ѕ��Bz�B]�=t��.�Ѕ��Cz�B]�-t��.�Ѕ��Cz\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0@�xypa�&]7K�yoV�kB�����_�U_z2h�k��q�\\ֿm����!=�Mp&�W���[�m��Ď�tW/�~.CS����8���nW�ٕ��ʞW�����n��W�F���� {wl|W��:�i��gڼ����C�Ѫ��x��nɏf=�������绿x}�?^>�Z7��CٺtE���R���JO<'),(100,100,100,800,'x���?n�@���� �HԐ���>�VHi9\nZ��0Y������K QP�QQrz����9�w��G�7�7_~�A�娲\Z_�V���:�E\\�&>3ͱQs��\\�5�dz!�s_o�,5��*��I*�b�iU�+�3�V]����e*3oת�HCF2f$��$�ui���*T,�f��ξ�~����s$�5\"�C�9\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�Q���ٙx����{o�Եڽ����w�i*�����P.׳�ۅ�Ǖ��Ҷ�L\\֙-��֥U��+�V�$�i*sS�7�Wy���d�J�]�mW�v�.Cq�]�7�N�c���Ɨ�b�5a���ϴ���PѩL���ևq���!���ivs������\\>�X�o�T�]��?)�������'),(100,100,100,900,'x���?n�@���� �HԐ�@g`+�4��4L�#�D����Ʀ@B�H�tT)Rpz����9�w��G�7�7_�N�e��\Z��V���:�E\\�&>6́Qs��D�5�dz&�s_��,5��*��a*�b�iUv��1�Z]����E*3o������d�H�In�Ҫ��P�蚵n;������՗H�kD���\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�����1�Z]?����k�{��ѧ���T&�2k�[�5�\\.g÷]�+��mՙ��3[Ĺ�K���O�Re\\�H���M�\\�^�I\Z���+�v%�]ɋ�U�ŵw����;����*�w4���	3��������d�����>�����ܜf7��~�0�?��W��+=��K���OJv���ߩb'),(100,100,200,0,'x��Ͽn�@��9��\n\"�HԐ�@gK�W!�I��Ұ9�|�빋M��cPP�QQ�P�H�\0��DA���gW+��o��O�k�VV�K۪3qYg��K��g�96j�_>�qM\"�^�~��u����^%�8Ie^�V� �2���I�Z]�2�v�*Gi�Hƌd���.��>9��Y�ᵳ/�m~|��t����\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`�z�we6&^�+�ޛ>u�v����|���Df�{c�&���lػ����\Z_�V���:�E�ۺ�����.U�5�d���T�U�U��+��mWrӕ<�]��P\\{��굼�;阭rtK�P��0���gھ�w��T&]Џ�\n���|��ww�o�Sn��0�?��W��+=��k���OJvs��HF'),(100,100,200,100,'x���?n�@����DH��!���>�VHi9�4L�#�D����Ʀ@�H�tT�\\\0j�����7 \nr��H������|�){����j|i[u&.��qi���4�F��2�I$���}�����֫D\'�̋�Ҫ�Wfc���\"I]��Tf�.U�(\rɘ�l3��֥U�\'�b�5k?�v���͏�#��y|��\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�Z/�����kuE��{ӧ������ￚS��Ȭu�mׄr��\r{�TV�K۪3qYg��s[�V}�ۥʸ&�LS��j�����4t%cW��Jn��g��t�k�rW��7r\'�U�ni|*]f:��L����ʤ��[a�ڝ��!����mwf��x���+���޵�t�\'%�9=�I'),(100,100,200,200,'x��ѽn�P��/?�@�T�Z��Ȅԥ�+��օ��(q���>I$.���#7\03���\r0���@�O��گ�ǖ�s��}�M6�Tvb����x��g��Ȍ�C3�3��=��\ZMi�V?M&q/03�:i��9��dN��S35��EC?�2�	��ڮs�3�r�����m2�.��GŊN>���~|�h���yS$�<�.o\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�J	���(��*�Ѕz�BM�G(�Є��C�P�	=����Cz�\n=4����Cz�B=�Ղ���˴�=�z\\�=�QG���*-�A�:�jzУN��uZЃup���G�C����=��-�A�*���hAzTѪZЃUD=Vق���U��=��z��=�Q�jAzT=�Xgz��6[wz��6����=���R�wj�ƛ�h�=IS3���o���ɼkIc_�Y����b�������� ��;����7Jzv��m2�.�yG�뤼\ZMi�i�� I�<�]~��_����I�N�8I�~���r\'(g;ٽ`�bE\'g����_�}��4�¬�����|f��w�}^>/��������F��;����������q2�'),(100,100,200,300,'x��ѽn�P��7�@�T�ZX�}����rUT�*!N���T��OR���������\r��e qL8q\nb�ly^\'O���v���>�����~jCta��L4��v��$:1�C��S�^����ew�i�O��� ��D:��y�����L<\rn\'.�D���B���<#�Έ�g���m������[L�~�i\'�_=�}���)RLD���v���ߵ�CW���:-�A�:�nzУN�COzУ�,�A�:D=!-�A͡-�A͡-�˂��ڀ��ڀ:-�ߟz-�/�&���Vz��=�ޜ��\Zzoz�Cs��Qz��-�9�N��>��������҃�C�K��Z�C_��hA͡w���{�7�=��ޓ��zOzl�=�ސ��zGzl�zCz�Ck���A͡����Z�C_�MiA͡w�G�=ЛуZC�Ezh�=�ǂ�BoL�zz�w�=��ވ��z#zl�=�ޛ��zsz���=���	=�A����=4�ޟ��ڀ:=�;�(�r���L4\rn=������ݷ��_���4����7�����U{�(�z��]�<8���E��m������T�FSZ�tL�f>�ä�+�w�7w��2��nyq�������I����r���n1)�i��ϴ��?�<�FQ6��V������|q��ջ_W�>��/���ñ�\\�~�>�����_'),(100,100,200,400,'x��ѿn�P��/��\n�3�,���	�K+WE����Q�*��}�:H�+CG6&FF��$n�	\'NAt�+�X�%�s��c=~�NY�~�Ct�ː�h��$��Yt�f�.����\\��u�mwT��l���/��Ϗ�MV?�`{ɥ[�x�I��e�\'�-� ;H�q3#^ψG>��P,�zE��͋��N>�x����}۬��=ޫ�8�ڶ;Ђʶ݂Z=���j衅Z衅Zh��Zh��Z衅Z衅Zh��Zh��Z衅Z衅Z衅Zh��Zh��Z衅Z衅Z衅Zh��Zh��Z衅Z衅Z衅Zh��Zh��Z衅Z衅Zh��Zh��Z衅Z衅Z衅Zh��Zh��Z衅Z衅Z衅Zh��Zh��Z衅Z衅Zh��\0\0\0\0\0\0\0\0�Rv��-\\4�$zRn��e��|���X�Ⱥe��W�z�]uW�U��A�Ct�ː�h��$\Z�|�C���� Xs���I���8/�=L�]q�+^�ov�O�4�zq^��4{i��N��vp��g��~5�ϴ��3����P�ckU�e�Z��s���r5������ݷ����=�7��h�m�*���)Û�_*z$�'),(100,100,200,500,'x��Ͽn�@��9��\nR$jHhBg?�UHi9\n\nti؜W������Ŧ@�1((�QQ�P�HtTT��	��<�w��Wk��o���i��UV�۪3qYg��K��\'�94j_�ʸ&�L��~��u����^%:?Je^�Ve7�4���I�Z]�2�v�*�i�Hƌd���.��>9��Y�ᶓϯ6�?]G\"]#�t7�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�^^���������O]�ݻo��1�29�Y��خ	�r5�]�z\\Y�/l���e��\"�m]Z�}|f�*�D2Men���*O�Е�]ɶ+��J�׮�E(���]�Z�ʽt�Vٿ��E�XtM�i��3mo�;Tt,�.��g��kܢ�vH~p���M�ϻ_<�����u�J�e��e������+��'),(100,100,200,600,'x���?n�@���� �HԐ�@g`+�4�\n�4L�#�D����Ʀ@��)(�(�\0��P1^\' \nr��H�<zo~o>�����^e5>��:�uf��4M|l����ŉ�k��L��^WYjz�U���T���Ӫ�fc���\"I]��Tf�.Ue?\rɘ�l3��֥U�\'��b�5k?�v����͏�#��y��O\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�Z/�/���kuE��{ӧ�����W_͇�Leֺ7�kB�\\Άo�VV�s۪3qYg��s[�V}�ڥʸ&�LS��j���ʣ4t%cW��Jn��g��t�k�rW���r\'�U�oi|*]f���L���ɤ��Ya�\Z��};$߻>�v}���#�s�|e]��#ٹt����d7�o\"���'),(100,100,200,700,'x��Ͽn�@��9��\nR$jHh���*�4��4l�+�F��z�bS ���(=�<<\0�s� O�]��W3����CvZ/{���ܶ�L\\֙-��4�i���\'2�I$�3���z]e��W��S��O���^��I��$u�.R�y�T��4d$cF��Hr[�V}����E׬�p�񧗏7�?^E\"]#�d7�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�^�_���������O]���o�}1�29�Y�^ۮ	�r9�]�zXY��m���e��\"�m]Z�}|j�*�D2Men���*��Е�]ɶ+��J�ծ�E(���]�J�ȝt�Vٿ��y�XtM�i��3mo�;Tt$�.��g��kܢ��|��������_<�����u�J�d��e������j���'),(100,100,200,800,'x���?n�@���)5di���R�D���ti��Gމ���ۍM��1((�QQr�Ps$.@�x��(�	�#}<������S�\Z/����6�LTT�ͣ��щ���óS�h,�sy��jS���W�%2���Fe?�4[o��q�\Z]$2�v�*�$d�CF�ˈ3[V}/CŢ�7�������_��\"m-�b?�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\04^_���6�����K\\���O>ޘOɴq�l[�r����6t=-�F�Qg��Jme�*��.ZڕʰFc�$23�j]y�gI芇�x��uů*W�\"W�e�|+��A2d���i|*mf:��L���5>�Qtó��5l�e�\'?�=�v{:�����\\>[[���X��\\��?)�����b'),(100,100,200,900,'x���?n�@���� �HԐ�@g`+�4��4L�#�D����Ʀ@B�)�((�\0��P1^\' \nr��H�<zo~o>�����^e5>��:�uf��4M|l����ŉ�k��L��^WYjz�U���T���Ӫ�fc���\"I]��Tf�.Ue?\rɘ�l3��֥U�\'��b�5k?�v����͏O\"��y��O\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�Z/�/���kuE��{ӧ�����ￚ��Leֺ׶kB�\\Άo�VV�s۪3qYg��s[�V}�ڥʸ&�LS��j���ʣ4t%cW��Jn��g��t�k�rW��7r\'�U�oi|*]f���L���ɤ��Ya�\Z���vH�w}������/F����ʺ|�G�s�2]�I�n��C�*'),(100,100,300,0,'x��ѽn�P��/��@�T�ZX�-�fB���UQa��8m�WI��a@�e00�11r�\\��ӴB��$9��7қ(�����|�%+Y*�C�����m{A��]3�2�l�ޓ�є�Y��hЎ�ئN�ۑ��&_2\'�ѱ91��Ž0�3׊d9�G��fT�N���;6�[�����V>����~{������M�|(�t��x����>[�CO{�����z��xԵ�X��G]�CO׵���u<�[x��x�Q�nc�u	=��<��],����ᡧ�Z��GU��xT5<�4+<�Z��������fm�Uix�Q���Ӽ,������<�z��x��EX��GY�CO������H<�([x�i�x�Q�|X��GY�CO�,��C{>-��C{x�ɷxhͷxhη�,��C[����=<�Z�/�3�<��{�x��C_�g���=<���{�x�5�s����3��C_�g���=/<���{�x�5�s������RyplNL0rq/x��fř��~<���|Z�ƶ,g�{����ty��\r�m�b�������K���=r2}5��ɪu����XNW����U��$�Vqs�Ɲx�N>Ƚh����+�*�h���L�>������#��h<�[ŵ7?>�&;߿����\\��\'G���]w�nGVN��^�\"������	�'),(100,100,300,100,'x��ѱn�P��/N��\n�3�,� �2!ui媨�UB��Wɭ�\\ߤ/���������H�N�1��;IO���v�s~_�^��0�!��Up&����SS�g�<6��:��Չ�{)�#_��,5�D�\'��\'�?� 鵙�d�$I]���� GisFҞ���HF�����EsŠ.g~�ig��<����%�K�����;����=v�C{Oz�9�=�;��K{CZ�9��{�҃�i�Gz ��=vׂxioJz ��+=��C{3z�5��A����ݱ�^�ӂ�i�L����ވ�@M{z�9�}�qw-聗�洠rڻ��C{z��C{���=��ޟ�@Nۀ�@Nہx� �mA,��=��V���X�+z`E�h�=��V���X�+z`E��=��V���X�+z`E��-��V���X�+Z`E��-��V���X�+Z`E�h�=��V���X�+z`E�*/���ĳ�&�s��\"uU�?�|���ܕΉ�*���es����?��G�\r񕭂3���$�bj�_�v�}u\"��7�p\\� ��殤�+Yݕl�J^.���»����{���g9��Ɨ���l����ϴ����J�nZ�_�y����������>[��������χ��֍��T�n\\ƛS$���:�1Q'),(100,100,300,200,'x����jA��_�4�P�����I�eK�z+���LIv��I�� �<z���г�!���d�{��]�$d3��Y��N�e��!��up&����&3S%��:2�=?���t�w!�c_΋<3K�t/�3LW?� {ٕY�t�4�\\����� Y���3���tl˙\r~���æ����N?�|����CW��D������=t��.�Ѕ��Bz�B]�=t��.�Ѕ��Cz�B]�-t��.�Ѕ��CZ�B]�-t��.�Ѕ��CZ�B\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0��j/����$�����2suh�~���yߓα�k��6U\\.���gw=(lH.m�Ifen��ؖ3�29�� ���J/��)F��y��]i�+]�J��ҧ�+�0..��╼�;Y;;��-��æ�g����O�}��t�hپV��b���z5����������xu�L�O�\\�<L�S$�~���m�'),(100,100,300,300,'x���?n�@���)5$4� �\0[!�I�((�����Gމ���ۍM��1RP�QQr���;0^c!\nr��H��zo~o������Ai5���:Uj�0utj�#����L�5���Rf�ڔib:�U��ǉ,��Ш�\'�fk⍺<N\\��D�ޮT�0	��2��V�U���b����v������o_�\"m-�b?�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\04^_���6�����K\\��On���L�e޸��C����o���V�+ۨ3QQ�6�2[V}�ەʰ&S�%�0�j]y�gI芇�x��]��ʕ�ŕw�+��Gy��*��4�	˶3�������Ȥ\r��Y���qo��GN/����#�s�bm]��ٻq���I��7�5�@'),(100,100,300,400,'x��ϿnA����qB�D\r	\r4��\\!�ItQPH	����{c��7�����JIIC�c ���l!\n�ߕ>�����f������Ai5���:Uf�0u|j�#����L�5�d|)��Z�Yj:�U���T�yhT��k�6�J]����Y*o�r���d�H6��V�U�%�b��+��v�������/�H[�<��g\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0����fm╺<~��R�h���O?�籌�eҸw��C��L�o��V�+ۨ3qQe6��*��.>�s�a�\"�25�|Yy�\'i�J��dӕ캒��+u�+��|+��^:d����*T��:�t���6��*:�Qtó¿���{�\'?؞�o��w���˧K�K=����r�\"�n���'),(100,100,300,500,'x���?n�@���� �HԐЄ��\n)M\"GA�.b�y\'�?�o76Ǡ��������0^g�(�	�#}<������K�Z/���ʶ�L\\֙-��4�i����W�2�I$�K���z]e��W�.OR��O���^��I��$u�.R�y�T��4d$cF��Hr[�V}�\\��E׬�p�ٷ7G��_?G\"]#�t?�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�^^���������O]�݇�>~7��29�Y��ٮ	�r3�]�z\\Y��l���e��\"�m]Z�}|a�*�D2Men���*O�Е�]ɶ+�u%/jW�\"���z+��^:f����2T,�&�t������*:�I�����q���!��������/F����ʺ|���w�2]�R$��\0�]��'),(100,100,300,600,'x���?n�@���� �HԐ�@��l��&���@�	1Y�������PP�QQrz�����B�ߑ>y����|�-{����j|i[u&.��qi���4GF���3�$����}�����֫Dǩ̋�U�O���$kuE��V�̼]��a\Z2�1#�f$��K��O�CŢk�~�����Ǜ_߾D\"]#�d?�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�^�_���������O]�݇�>�0��29�Y��ٮ	�r=�]�zXY�/m���e��\"�m]Z�}|n�*�D2Men���*��Е�]ɶ+�u%�kW�\"���z+��N:f����\"T,�&�t������*:�I�����q�>�C�ӧ7��n�0�?��W��+=��k��j�\"�n�����'),(100,100,300,700,'x��Ͽn�@��9��\nR$jHh�A�\\��&���@	�9�|�빋M��cPP�QQ\"zz	���9\'DA����+�f�7����^*��mՙ��3[ĥi�S�5G/�d\\�H�r7����R�[�]�2/��Ve?�2���I�Z]�2�v�*�i�Hƌd���.��>9��Y��ӯ�o~~��t�ȓ�|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`�z�e6&^�+�gޛ>u�v�<���|���Xf�{k�&���l�v��ae5���:�uf�8�ui���]��k�4�����ګ<JCW2v%ۮdו<�]��P\\{��ꍼ�;阭rxK�P��0���g���w��D&]Џ�\ng��=����nN�ޜ~�����\\>_Y���D��]��]�d���o�b'),(100,100,300,800,'x���1n�@���� �HԐ����\n)M\"GAK�H��z��k��ۍM��1((�(sz�����B���~y��|o������Qi5���:������񙩏���s�(��<�}�)��t֫D�\'�L���Q9L���$uE��F�L�]��,\rɐ��2��Vk��K�b����vv��������H[��8��\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0����fk⍺\"~��R�h����?̗��NdҸ��C��L�o���V�+ۨ3��l�Z[�]��K�a�\"�25�rUy�gi�J��dו컒ו+u�+�rW���� �Uf�4�	�3������Ne���p�v�ǋ�O~tw���tv�����|��._�ܸLW�����	�,'),(100,100,300,900,'x���?n�@���� �HԐ�@��l��&���@	1Y�����	�KPP�QQPpz�����B�ߑ>y����|�-{����j|i[u&.��qi���4GF���3�$����}�����֫Dǩ̋�U�O���$kuE��V�̼]��a\Z2�1#�f$��K��O�CŢk�~�����Ǜ__?G\"]#�d?�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�^�_���������O]����>�0��29�Y��ڮ	�r=�]�zXY�/m���e��\"�m]Z�}|n�*�D2Men���*��Е�]ɶ+�u%�kW�\"���z#��N:f����\"T,�&�t������*:�I���٫q�~k��{7�OoN?���a�.����Wz\"{�.��.E������'),(100,100,400,0,'x��Ͽn�@��9��\nR$jHh��%ګ��$r��ds^�6�?�so@���������ǈ�;�>�	Q�\'��������f������Ae5���:�uf��4M|j�#����k��B�^VYj�֫DǩL���U�O���$KuE��Vg�L����a\Z2�!#�d$��K�~����Y�,}����竻o_\"��y���\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0Z/�����KuE��{�N]���_On��cˤu�mׄr����.t=���W�Ug��l�.��u|n�*�\ZE2Nej����*��Е]ɦ+�u%�kW�,���z\'�A:d����&T̺&�t���6��*:�Q��k��۟/�>��v��vW�q?�?�O��=����b�\"�������'),(100,100,400,100,'x��Ͻn�@��?���\Z\Z���\n)M\"GA!]\Z&�w\"�1>��)��*��tT))i�$��zW��\\�;�c��s�;����5^J�ѕmԙ��R�G���SS5Gg2��X&��0�ժL�Y�2�<Nd����~rm�&^����5:Od��BU���&#�lUX�]|*�m���i���<_���2ik��!�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�����ڬM�R�G/�7]�\Zm?�|r��|���X��{g�:��ʹ���ii5���:Uj�(�Ua�wѹ]�k4�I\"3S.��Wy���x�7]�+~U�R硸�.s�[y/�![���סb��a���ϴ9��P��A7\\+�}�����ɏ������7�G����Һl�\'�w�R]�R$ݽ�\0�۩v'),(100,100,400,200,'x��Ͻn�@��?���\Z\Z���\n)M\"GA�.\r���;�����H�-%e�4PsH�\0�����w�ǖ���w��/�k��V�Kۨ3QQ�6�\nSG��>2j�^�ɰFc�\\���W�2MLg����8�Y�4*�ɕY�x�.���<���U9LBF<dě�8�Ua�w�y��������޼~�����X��E���3\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0����+�6�J]=��t�k�}����o��DF�2m�[�֡\\����\r]�K�ѥmԙ��R�G��\n�����BeX��L��r���ʓ$t�CW��w]�ʕ:ŕw�+��;���*�w4��3�����\Z�Ȩ\r��Za�v��w�\'?��~��~�����>[Z�-�D��]��]����Fʪ>'),(100,100,400,300,'x��ϿnA����qB�D\r	\r4��\\!�ItQPH	����{c��7�����JIIC�c ���l!\n�ߕ>�����f������Ai5���:Uf�0u|j�#����L�5�d|)��Z�Yj:�U���T�yhT��k�6�J]����Y*o�r���d�H6��V�U�%�b��+��v�������/�H[�<��g\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0����fm╺<~��R�h���O?�籌�eҸw��C��L�o��V�+ۨ3qQe6��*��.>�s�a�\"�25�|Yy�\'i�J��dӕ캒��+u�+��|+��^:d����*T��:�t���6��*:�Qtó¿���u�\'?؞>n��w���˧K�K=����r�\"�n��U�'),(100,100,400,400,'x��Ͻn�@��?���\Z\Z���\n)M\"GA!]\Z&�w\"�1>��)��Z��tT�)i�$��\ZQ�+xGz<\Z���|�%{����jteu&*���Qa����GF��řk2�٥<�|�)��t֫L/�Y���Q�O����uy��F��̽]��a2�!#�eę�\n����CŲ�7��������ϯ��\"m-�b?�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\04^_���6�����K\\��O>}7�3�˼q�l[�r����6t=-�FW�Qg��Jme�*��.:�+�aM�2Kda�պ�*ϒ�]�+��W�+u�+�2W���� �U�i|*�mf:��L���5=�Itó¿o���ɏ��>���/�G����ںl�\'�w�R]�)���oD��'),(100,100,400,500,'x��Ͽn�@��9��\nR$jHhBg?�UHi9\n\nti؜W������Ŧ@�\rh)(�()i�y$:��9\'DA����+�f�7����^*��mՙ��3[ĥi�3�5�/�e\\�H��r?����R�[�]��2/��Ve?�6���I�Z]�2�v�*�i�Hƌd���.��>���Y�ᶳ���6?�|�D�F��~>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00j�<�6���3�M��V�w?}�n>Ner\"�ֽ�]��f6|�����\Z_�V���:�E�ۺ�����.U�5�d���T�U�U���+��mW��J�׮�E(���]�Z�ʽt�V9���E�XtM����3mo�;Tt*�.��g��o��C�������_<�����u�JOe��e�ڥH���\0\0���'),(100,100,400,600,'x��Ͽn�@��9��\nR$jHh���*�4�\n�4l�+�F��z�bS �R�RP�QQR���H��sN��<�w��W^��o��o�k�TV�K۪3qYg��K�ħ�92j�^�ɸ&�L/�n��u����^%�8Ne^?��~ze6&Y�+�Ե�He��RUӐ���6#�m]Z�}r*]���m��^=����9�\Z�\'�!�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0���r��lL�VW�O�7}�Z���|���4�ɱ�Z��vM(�����B���j|i[u&.��qn�Ҫ��s�T�$�i*sS-W�Wy���d�J�]ɮ+yV�J���.w�ky+w�1[����b�5a���ϴ���PщL���ξ�{����ݜ~�9}y�����|��._��]�LW��v����b'),(100,100,400,700,'x��Ͽn�@��9��\nR$jHh���*�4��4l�+�F��z�bS ���tT������@�g`}�	Q�\'����ʫ���~�%{����j|i[u&.��qi���4GF�ы3�$����}�����֫Dǩ̋�U�O���$kuE��V�̼]��a\Z2�1#�f$��K��O�CŢk�~�����Ǜ��?F\"]#�d?�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�^�_���������O]����7�29�Y�^ۮ	�r=�]�zXY�/m���e��\"�m]Z�}|n�*�D2Men���*��Е�]ɶ+�u%�jW�\"���z%o�N:f����<T,�&�t������*:�I���ٷq�~�C��w7�_o�0�?��W��+=��k��j�\"�n�?��*'),(100,100,400,800,'x��Ͻn�@��?���\Z�4���R�D���4L�#�D�c|vcS q��tT�)��sH��uV��\\�;��G��w��o9h��V�Kۨ3QQ�6�\nSGg�>6j�ߜ˰Fc�\\���W�2MLg����$�Y��4*�ɕٚx�.���\"���+U�\'!#2�]F�٪��x*m���mg7o�o}�:ik��!�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0������lM�Q�G/�7]�\Zm?�|����2�щL�޶u(��i�mC���jtiu&*���Qf�ª]�k4�I\"3S�֕Wy���x�w]�+~U�R���.s�;� �![e~O��P�h�0���g���w��ڠ��n�}�l��Gw���N������gk벵����Ku�O�t���)��'),(100,100,400,900,'x��Ͽn�@��9��\nR$jHh���*�4��4l�+�F��z�bS !�\0��tT�4�<����� O�]��W3����[�Z/���Ҷ�L\\֙-��4�i����g2�I$����z]e��W�.�S��O���^��I��$u�.R�y�T��4d$cF��Hr[�V}����E׬�p�鷗�7��|�D�F��~>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00j�ܿ2���S�M��V��?|�a>Ner,�ֽ�]��z6|�����\Z_�V���:�E�ۺ�����.U�5�d���T�U�U��+��mW��J�ծ�E(���]�J�ȝt�V9���y�XtM����3mo�;Tt\"�.��g����>����nN�ߜ������\\>_Y���D��]��]�d����'),(100,100,500,0,'x��Ͽn�@��9��\nR$jHhB��h�BJ��QP�K��y����X�]|H<EJ:*J^���@��`��(�|W�xe{f�_�^�tj�]�ޚ�J]n\n[�3[[����eX�HƗ�8ղL�vA%�<Id��/��~rcW6^����7:Kd�\\U�.#2�MF���p\Z��EW1k�e�O;�~u����K$��\"/���s\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�&����f�>7�C���7�~����{7�щL\Z���uW.����v]�K���5�)���&sU�4�ͅ��k�8��-�*��H��x�7]�+~S�Rg]q|����Q%C����o��Y[w3������Ne�v�õD����WM��d[y��*߸�ç糅��ޭOu�K�t�����w'),(100,100,500,100,'x���?n�@���� �HԐЄ�>�VHi9\n\n��0Y����/�@�HIGE��s$:�x�+DAN���������/�k�TV�k۪3qYg��K��g�96j�/�eX�H�W�8����R��^%�:IeZ�/��~zcV&Y�+�Ե:Ke��\\UӐ��&#�m]Z���\"T̺f�������~~��t����|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`�zyzcV&^�+�Wޛu�Z�>�x������D&�{o�&���v��ye5���:�uf�8�uiկ�;W�(�q*SS��Wy���d�J6]ɮ+y]�Jg���.w�;� ��![����7�b�5a���ϴ9��Pѩ��`=\\K$:������O����_��o܏���Ӆu�BOe��e�إH���\0�k�?'),(100,100,500,200,'x��Ͽn�@��9��\nR$jHhBg?�UHim�Ұ9�|�빋��Ǡ��������@����>�	Q�\'��������f����&�A�4�r�zU��u|f�c�����k��Rf�Z���kT��#ӼiT�͵]�d�>O�otfd�\\UM���&#�\\U8\r�䢫���2���}{s����s$��\"����s\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�&��k���R}����o������[�i,��4��k�\\n&����N�+ר�qQ�.�3WN�:�ps�a�\"��r����3�u%CW��Jv]��ʗ:늫�3_������*��4��*fm��t���6��*:�Q�Y����?7}�m��׻�o܏���Ӆ��BOe�Ƨ�إH���\0&Z�'),(100,100,500,300,'x���?nA������\Z�Р��BJ�h���.b��\'��>;k\n$�AAIGE��s$:���� \'�����hߛߛ��d�	rP:��]���E��<.l���ت=�<�a�\"_��,T�25v�Jtubd���Fe��ؕM�����FgF&��U��tɐ�l2��U�ӰN.��Y[/C���ۣ��o_\"��y���\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\04A�ؕ�����evm|���O>}���2:�I�߻����v�ۮ�i�4�v�zU��8sU�4��7W�(����-�*�<3]W2t%��dו��|����\n>��;� ̐�rxO��b���L��is�ߡ�S����,��h��M�\'?ڞ~o�����˧糅��ޭOu�K�t�����'),(100,100,500,400,'x��ϿnA����qB�D\r	M���R�D�4l|��F����s�@�\rh)(�(S���1��x\0�|�y��J�[�nf�_�^�t\Z_�F���*uy\\�:>���U{��\\�5�d|)�P-��ص*�剑i�\Z�}smW6Y�����7W�C�e$CF��H2WN�:��*fm��mg�o�V?�}�D�Z��~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`�y|mW6^���!ص�~<���~��D&��ں+��I�m�������5�m\\T����U�Ӱ�/�\\eX�H�F���/����t]�Еl��]W��κ�*�̗o�<0C���=����Y[w3������Ne�v�óD����O~�=����q?�?�O�g=����b�\"�n��ũ�'),(100,100,500,500,'x��Ͽn�@��9��\nR$jHhBg?�UHi9\n\n�Ұ9�|�빋��Ǡ�LGE���1��x\0�g,DA����+K3���S�Z/���ڶ�L\\֙-��4�i�����s�$��<�}����l�W��NR��O���ޘ�I��$u�.R�y�T��4d$CF��Hr[�V�6���Y�~�ٷ�G�_�\"��y���\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0Z/Oo���kuE��{�M]�����>ݛ�S��Ȭu�mׄr����.t=���׶Ug��l�.��m|a�*ÙD2Men���*/�Е]ɮ+��׵�t�k�rW���(�Uh|*]v:��N����Ne���Y\"��x�}�?������+�3|��._��ݺLWc�d���\r�b'),(100,100,500,600,'x���?nA������\Z����R�D\n�4L���D���ggM�D�\r((�(�\0=�@��\0�zmE)�	�#}v4�������k��N͕k�[ST��Maksf�c�����k��Rf�Z�ib�.�D�\'�L��Ш�\'�ve��<N|��D&��U�0�2�!#�dę�\n�a_t��^����o�V���D�Z��~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`�y|mW�,���Ev��Fۏ��|�i��et\"�ƿwmݕ�ͤ��]��ҩ�r�zk�*u��\\U8\rks��*�\ZE2Ndj���\n*ϒ�+��MW��_V��YW\\���|�ɐ�rxO㫮b���L��is��Pѩ���zx�Ht��?5}�m����7������Ӆ��BOe�Ƨ�إH����?�+'),(100,100,500,700,'x��ϿnA����qB�D\r	M���R�D�4l|��F����s�@�1((�(Q��<T<\0w>[��<�w�ϭ�4���/?e�	rP:��\\���E��<.l���ت=~}.�\ZE2���Y��ej�������4��ʾ��+�,���ΌL���ʡ�2�!#�d$��\n�a�\\t��^����oo�V?�~�D�Z��~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`�y|mW6^���!ص��?�xg?�et\"�ƿsmݕ�ͤ��]���i|�\Z�6.���q��iX�n�2�Q$c#S[�UPyf��d�J6]ɮ+yY�Rg]q|�˷�^�![���W]Ŭ����?����CE�2j;��Y\"��v���ɏ����o�q?�?�O�g=����b�\"�n�.��'),(100,100,500,800,'x���1n�@���� �HԐ�	�}����$�(h�K�d=�N����Ʀ@��tT�\\��c �q\0�uV(Er��H?��zo�7���A�p\Z_�Z���e��xm���V\'V����(��<�B�)Rc[T��S#Ӽ?�*���nm�Q�\'��:72	n�*3�e$CF��H2W���6Yt�ڄ������D�J��a|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Pyzm�6ި��W!���Z�O��}�i��et*��pMՕ�ͤ�6]���i|�j�6^����̕k���n�2�Q$c#S[,WePya��d�Jv]ɾ+y]�B�]q|���Q�![e�@㛮b�T�LG�ϴ���Pљ��N;<K$:���V���;{���w.����Vz&7>��>E�������'),(100,100,500,900,'x���?n�@���� �HԐЄ�>�VHiM��0Y�����	�KPP�QQPpz��D���Y!\nr��H��zo~o>���&�A�4�r�zU��u|f�c���幌k��R�g�Z����*�剑y>\Z�}sm76Y���]��T�C�g$cF��H2WNC�\\���^�ᶳo��6?�|�D�Z��~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`�yxm76^���g!���F�w?}�n?Ner\"�ƿqmݗ��l��}���i|�\Z�6.���q��i���T�$����-��*�<1}W2v%ۮdו<�|����\n>��ky+�̘�rxG㋾b���L��i{�ߡ�S���n|�Ht4�ӯ͐�������w�x���+糕��ލOu�K�t������'),(100,100,600,0,'x��Ͽn�@��9��\nR$jHh��%ګ��$r�P �as^�6�?�smހ�2%/@�c ���wB�	�+}ly=��ٯ�e��rPY��l���e��\".M���Ȩ9zs&�\ZE2������U����*��q*Ӣ�hU��k�2�R]����Y*o�r���d�H6In�Ҫ_\'�b�5Kߟv����կow�H׈���g\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0����fe⥺\"~�Y������\'��/cˤulׄr����.t=���W�Ug��l�.��u|n�*�\ZE2Nej����*��Е]ɦ+�u%�kW�,���z/�A:d���x*f]f:��L����Ȩ�õD���ϗm��h��v�+�߸�ç���ލ�t�K�l����@'),(100,100,600,100,'x���?n�@���� �HԐ�@g`+�4��Hi��Gމ���ۍ���D���KE��9w`��� \'������{�{s�[�Z/���ʶ�L\\֙-��4�i�����3�(��<�}���Ԭ�W�.�S��G���^��I��$u��R�x;W��4d$CF��Hr[�V�:9��Y�����o��~}�\Z�t�ȋ�|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`�zy|mV&^�+�ޛu�Z�>�|����2�ѱLZ��vM(��I��B���j|e[u&.��qn�Ҫ_��v�2�Q$�T���/j��,\r]�Еl��]W�v��Bq�]�w�A�C���=��CŬk�L��is�ߡ�u�z��H�i���mw�lw��o܏���Ӆu�BOd��e�إH�{���'),(100,100,600,200,'x���?n�@����DH��!�!�}����$r(��0Y����0~��)�h�%%��c q*��]!\nr��H[�7�7��^�尴\Z_�F���*�˸0u|n����Ņk��J�s_��,5��*��i*�e�Ѩ�7fm���e��Fg�L����Q\Z2�!#�d$��\n��K.CŬ�W�?���\'�_>E\"m-r|�/\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\Z/n���+u�����K]��?|3�2:�I��ض�r;�m�zTZ��m���E��e�۪�����U�5�d��Ԕ�E�U��+��MW��J�U��Y(���]�Z�ʽt�V9���y���u����3mN�;Tt&�6�k�D�?7}���������o܏���Ӆu�B�d��e�إH�{�ԩ�'),(100,100,600,300,'x���?n�@���� �HԐ�@�<�\n)M\"GA!R$�d=�N���n�H�܀��.%��H�{�+DAN��c���������5AJ��k�۸�R�ǅ��S[Y�Gg2�Q$�K���jY��]��]\'2���Fe?��+k��s��Fg�L����a�e�!�l2L��iX��b���Пv����կo_#��y���\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\04A^ە�����Ev��Fۏ?}�a��et,�ƿwmݕ�ͤ�]���i|�\Z�6.���q��iX��n�2�Q$�D���/���$���e6]f�e^V��YW\\���|�{ɐ�rxG㫮b���L��is�ߡ����p-����ߛ�O~��}��}~����9|�p>[����T�Iw�?�_��'),(100,100,600,400,'x��Ͽn�@��9��\n\"�HԐА�~����$r(�Ұ9�|������H�T��tT��4�<���|\'DA���ǖ�3����[�\Z/�����6�L\\T�]ƅ��sS�5\'/.dX�H�W���jUf��W��NS�.��F� �1k���-��5:Ke��\\U�Ґ��&#�mUX�]r*fm���i��^=Y���9ik��|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`�xypc�&^�[�O�7]�\Zm��|���4�ѩL\Z�ƶu(��I�lCף�j|mu&.��.��V�U�ŗv�2�Q$�T���/*��8\r]�Еl��]W�r��Bq�]����V�C�����CŬ��L���is�ߡ�3�A7\\K$z������ow_nw?�}�~��.��z&{�.��.E����?�b'),(100,100,600,500,'x���?nA������\Z����R�D\n�4L���D���ggM�D�\r((�(�\0=�@��\0�zmE)�	�#}f4�{�{���5AJ���5�)��妰�9���U{��\\�5�d|)�P-�4�kT�˓D�yiT��k���R}\'��Y\"���r�t�o2��U�Ӱ�/��Y[/C��ُ�G��߿F\"m-�|�>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00h�<��+k��s�\"�N|���_O>��_�2:�I�߻����f��m���tj�\\�ޚ�J]n2WN��\\��ʰF����r���ʳ�늇�x��◕/u�W�g�|\'�A2d��������u7���gڼ~;Tt*����%}ڞGM��h{{���{����<>]8�-�T�n|��]����Շ�+'),(100,100,600,600,'x���?n�@���� �HԐ�@7>�VHi9\n\n)��0Y����/-7HAIG���s$�B�ߑ>3\Z�����K�� �Ss�\Z��U�rS�ڜ��Ȫ=�8�aM\"�^��,T�2M�����8�y�_\Z����nl�V�ǉot��,���&]F<dĻ�8sU�4l��b���пv�����ϻ/�H[�����\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0� ���ƚ��ܼ�n�h��Ǔ����T&�2k�{��]�������N͕k�[ST��M��iؚs�T�$�i\"s[.WUPy�t]����+~U�R]q|��w�A$C���=����E[w3������Nd�v�÷D�O���ɏ��ތ��?�G������l�\'�w�S]�)���o̹��'),(100,100,600,700,'x��ϿnA����qA��\Z\Z��=�+�4�.\n\nHi��V�|�;wH��%%���1�����B�	�+}v����f?�����ai5���:Uf�qa����\'F�ɋ�(����Z�Yj:�U���T���Ҩ�7fm���e��Fg�L����Q\Z2�!#�d$��\n��K.CŬ�W����\'�_>E\"m-r|�/\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\Z/n���+u�����K]��?|7�2:�I��ض�r;��6t=*��׶Qg���2�mUX�]|i�*�\ZE2Nej����*��Е]ɦ+�u%�*W�,W��|-o�^:d����<T��:�t���6��*:�Qt÷D����w�\'���^n�ow���ǧ���ޭ�t�K�lw����'),(100,100,600,800,'x���?nA������\Zb\Z�v�\n)M���R�L���D���ggM�D�\r((�(�\0=�@����B�ߑ>;��{�{��V� G����5�m\\T�����񙭏���7�2�Q$�y��j]��ݺ�]�$2���F�0��k��s��F�L�[��,�2̐av&sU�4l͢����:�����|����k$��\"/��s\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�&��k���Z}��n�h��ד�?헱�Nd������r���߶�zZ:��\\���E��<�\\U8\r�x�*�\ZE2Ndj��\n*ϒ��]f�e�]�U�K�w�U�/��y��*�{\Z_w��f:��L����ʨ�l�g�D��}�h��Gw�o�Ng����˧+糕����Ou�O�t���3��'),(100,100,600,900,'x���?n�@���� �HԐ�@�9�VHi9\n\nHi��Gމ���ۍM��D�\r((�((�\0=�@���9�w��G�7�7_~�^�t\Z_�F���*uy\\�:>���U{��L�5�dz!w�P��4��*��q\"�|�iT��+��f�>7�ot��,���&}�3�6�d�*��Μ���^����o~}����ȓ�>�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0��	r��nl�V��OC�]�m��|���4�ɱ�\Z�Ƶu_.׳���]K��k�۸�R�Ǚ�\n�����Re\\�H���m�\\UA�Q�w���l�̮�<�|����\n>��ky+w�1[����}Ţ����?����CE\'2i{��,��øO�5C�ӗ7��o�0�?��W�g+=��k��j�\"�n���N'),(100,100,700,0,'x��Ͽn�@��9��\nR$jHh��%ګ��$�((Х���m���.>\n$�\"%%���1��3`��(�|W���zf�_�d�	rP:��\\���E��<.l���Ȫ=z{&�\ZE2���Y��ej�������4�?\Z�}smW6Y�����7W�C�e$CF��H2WN�:9�*fm��i��.��~}����ȋ�.�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0��	��ڮl�T�ǯB�k�m?�|�釽��X&��ں+��I�l�������5�m\\T����U�Ӱ���\\eX�H�F���/����t]�Еl��]W��κ�*�̗��<0C���=�o��Y[w3������Nd�v�õD��۟/�>��v��vW�q?�?�O�g=����b�\"����%�'),(100,100,700,100,'x��Ͽn�@��9��\nR$jHh���*�4�6\n\n�Ұ9�|�빋���@�HIGE���H���;!\n�ߕ>������_��9(��W�Qo�J]��Om}d�]�ɰF��/�a�e�\Z�vA%�<62���Fe�\\ەM�����FgF&��U��tɐ�l2��U�ӰNλ�Y[/C��ݛ竟_?G\"m-�b�>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00h�<��+/�������F�?�|�no�2:�I�߹����f�?ۮ�i�4�r�zU��8sU�4��s7W�(����-�*�<3]W2t%��dו��|����\n>��[y/̐�rxO��b���L��is�ߡ����p-�����ǦO~�����M�q?�?�O�g=����b�\"���\"��'),(100,100,700,200,'x��ϱn�@��9��\nR$jHh�[?�UHi9\n\nti؜W��|�Y�]l\n$������5��@�}�� O�_�g���f?���:�A��\\�Z�5�2u�Y�ʜ��Ȫ=zu&�\ZE2���Y(WE������8�i��*�ɕ]�x�>�_�,�IpsU9L��xȈ7q�ʥ����]Ŭ�V�?�����_>E\"M%�l�>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00��<��kkV�s�<�&�����Gn�Ǳ��eR�����r���Ϧ�z\\85��VoͲL]n2W.��֜��ʰF����b�(�ʓ�늇�x����/t���g�x#��^2d�������5U7���gڜ�w��DFM��%����]����7��ow߸�ç糅��޵Ou�K�t��i���'),(100,100,700,300,'x��Ͽn�@��9��\nR$jHh�A�\\��&�FA!]$�漺���X�]l\n$�����2���1����wB�	�+}ly=���/�d�rP8��\\���y��,�m���Ȫ=�8�a�\"_��E(WEjl�Jtyld���ʾ��k���g��ΌL���ʡ�2�!#�d$W�NC��w��Z�����7O�?�~�D�J��~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Pyxm�6^���!���Z�?}�n?�et,�ڿsMՕ�ͤ6]���i|�j�6���e������n�2�Q$c#S[̗ePyb��d�J6]ɮ+yY�Bg]q��o��3C�������YSu3������Nd�t��Z\"����u��`��m�����#�s�t��b�\'�w�S]�R$ݽ�\0��b'),(100,100,700,400,'x��ϱn�@��9��\nR$jHh�[?�UHi9\n\nti؜W��|�Y�]l\n$ހ�����2JEC�c �3`��(��W���zf��/�d�rP85��VoͲL]n��2��:�j�^�ɰF��/�~�U�&�uA%�8Nd����~re�6^�����:Kd�\\U�.#2�MF��r�4��yW1k�U�O;�y�t����H��D��w�g\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0u��Wvm�J}n��`���ڼ����w�i,�c����k��\\�\'���Nͥ��[�,S��̕K��5�n�2�Q$�D���/ʠ�$��+�tŻ��E��u�e�/��;���*�w4��*fM��t���6��*:�Q�i�k�D�����}����v���7�G������l�\'�w�S]�R$ݽ�\0Bn�*'),(100,100,700,500,'x��ϿnA����qB�D\r	M���R�D�4l|��F����s�@�1((�(Q��<T<\0w>[��<�w�ϮV���엟��9(��W�Qo�J]���l}l��>�a�\"_��,T�25v�Jtybd���Fe�\\ەM�����FgF&��U��tɐ�l2��U�ӰN.��Y[/C��ٷ7G�_?G\"m-�|�>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00h�<��+/������F�ߟ|����2:�I�߹����f��m���t\Z_�F���*uy���p\Z�񅛫k���Ԗ�ET���+��MW��J^V��YW\\��򭼗f�V9���UW1k�n���ϴy��Pѩ���z��H�k{5}���v�����#���t�|��Sٻ�.v)���?#���'),(100,100,700,600,'x��ϿnA����qA��\Z\Z��=�+�4�.\n\nHi��V�|�;wH��%%���1�����B�	�+}v����f?�����ai5���:Uf�qa����\'F�ɋ�(����Z�Yj:�U���T���Ҩ�7fm���e��Fg�L����Q\Z2�!#�d$��\n��K.CŬ�W����\'�_>E\"m-r|�/\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\Z/n���+u�����K]��?|7�2:�I��ض�r;��6t=*��׶Qg���2�mUX�]|i�*�\ZE2Nej����*��Е]ɦ+�u%�*W�,W��|-o�^:d����<T��:�t���6��*:�Qt÷D����}�\'��޾mϗw���ǧ���ޭ�t�K�lw�\Z詼'),(100,100,700,700,'x��Ͽn�@��9��\nR$jHh���*�4�6\n\n�Ұ9�|�빋��Ǡ�LGE����H��g,DA���gW+��o��O�k��N�+ר�qQ�.�[ǧ�>�j�.�dX�H���0պL�ݺ�]����Q�7�vc���<1�х�YpKU94]F2d$��$sU�4l��b���пv����͏/��H[�����\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0� �����k�y�2�5�����\'��OS�ˬ��\\[w�r3����zZ:��\\���E��<�\\U8\r���-U�5�djdn��\n*�Lו]ɮ+��W�/u�W�g�|+���U�i|�U,ں����3�^�;Tt\"����%�\ZϦO~��v7�������+糕��ލOu5�H:��bȪ�'),(100,100,700,800,'x��ϿnA����qB�D\r1\rtw�\n)M���]\Z6��y�����9S ��tT�(}z	��;_,DA����V������_r�9*�Ɨ�Qo�J]���l}l��9�a�\"_��,T�25v�Jtqbd��?�ʡ�������΍L�[���tɐ��2��U�ӰM]ż�ס�����盟߾D\"m-��>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00h�<����������Fۏ?�|����2:�I�߻����z�ۮ�i�4�t�zU��8sU�4l�[�k���Ԗ�UT���+��]W��J^U��yW\\���|�f�V��������u7���g���w��TFmg;<K$�=��E�\'?�;��;����~�.����Vz*�>��>E����S�N'),(100,100,700,900,'x��Ͽn�@��9��\nR$jHh�[?�UHi9\n\nti؜W������Ŧ@B�%((�((==��π}�	Q�\'����ʫ���~�%{M��ҩ�t�zk�*u�)lmNm}d��8�qM\"�^��,T�2Ml�Jtq��<~\Z����nl�V�ǉot��,���&}F<f�ی8sU�4t�y_�h�un;������珑H[�<���\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�� ���ƚ���<\r�v�o�}�������T&�2k�k��}�\\φo�w=,��Kר���R���U��Йs�T�$�i\"s[.WUPy��]��o��]W���.��*�̗���I�l��[\Z�����g:��L����Ȥ�u�D���>����nN�ݜ������\\>_9���D��}��]����;B�'),(100,100,800,0,'x��Ͽ��@��KB:�\Z.4Э%�TH��ɧC��\Z�╳\'�c=�9H<ŕtT��\0=���;`ǉ�ߕ>��������r�9)��kר���R������֧V���K�(��<�B�.��n]P������G�r��؍����8����T�Y�e�CF�ˈ3WN�6^t�^������o~}����ȋ�.�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0��	���n�Y��ͫ�6񍶟~>���ލet&��pmݕ���]��ҩ�v�zk�*u��\\U8\r[�pK�a�\"\'2��rU�gI�]�+>tů+_�+���|�^>ʃd�V��������u7���gڝ�w��\\Fmg;\\Kd���|��ɏ�������~�����Vz.G�>��!E���a��'),(100,100,800,100,'x���?n�@���)5di���R�D���4L�#�D����Ʀ@�H)�(�\0=�@��׻B�ߑ>�<~o~o������Ii5���:Uj�0uta�S���ͥk4�ɕ<�|�)��t֫������G�r�ܘ��7��8q�.�z�R�y2�!#�eę�\n�����b��ߟv�����o_�\"m-��8_\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\04^ߘ��6�����K\\��ǟO>�0w�ɴq�m[�r����6t=-�F׶Qg��Jme�*��.ZڕʰFc�$23�j]y�gI芇�x���W�+u�+�2W��� �U��4���3������\Z�˨\r��Z\"���秦O~�ߝ�w��o܏��ᳵu�Z���֥�>�Hzx��祥'),(100,100,800,200,'x���?n�@���)5di���R�D���4L�#�D����Ʀ@��tT�\\\0j�����w�(�	�#}ly����|�%G����jtmu&*���Qa���ԧF��K�h,�+���jS���W_�%2���F�8�1[o��q�\Z]$2�v�*�$d�CF�ˈ3[V}/CŢ�7�?����۟_>�E�Z��q�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00h�<�1[m���s�M��F��?}�f>Ndt&�ƽ�m��v�?�����\Z]�F���*�y�٪��hiW*�\Z�e��̔�u�U�$�+��]W|�_T��E(���\\�F�ɽd�V����2T,�:�t���v��j|.�6�k�L�����>��~w���~����9|��.[�ݺTׇI�?�ϩb'),(100,100,800,300,'x���?��@��\'���5lh�A��B�fW^-\nt+!f�3+�c��u(�8%%��H�;N�(�|G���������[�� \'�Ss�\Z��U�rS��\\��Ԫ=}})�\ZE2���Y��e�ح*��Y\"Ӽ�hT�����Z}\'��y\"����2K��xȈwq��i�Ƌ�b���Пv����ͯo_\"��yv�_\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\04A�؍5k��y��&�����G�~��c�ɤ��][w�r;�m���tj�]�ޚ�J]n2WN��,�ReX�HƉLm�\\UA�I�u�CW��]��ʗ:�3_��r/�Ufw4��*�m��t���v��*:�Q���/���4}���l�����#�s�t�|��s9����)��\0�i�,'),(100,100,800,400,'x��Ͻn�@��?���\Z�4���R�D���4L�#�D�c|vcS q��tT��4�\\��x�+DA�����9���[�\Z/\'����6�LTT�ͣ��х�O���W�2��X&Wr?�զL�Y�2�:Kd����qrc�&ި���5�Hd��JU�IȈ��x�g�*��.^��E[o|�ŷ�O���~���ȳ�|	\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`�xyxc�&ڨˣ�ޛ.q���>���|���L��{k�:������qi5���:Uj�(�Ua�w�ҮT�5\Z�$��)W�ʫ<IBW<tŻ�����\\��P\\y��򍼓{ɐ�2���e�X�u����3�N�;��\\Fm�\r��,���7}���|�����#�s�lm]��s9�u��)��\0�I��'),(100,100,800,500,'x���1n�@���� �HԐ�	�}����$�(h�K�d=�N����Ʀ@��tT�\\��c �q\0�uV(Er��H?��zo�7���A�p\Z_�Z���e��xm���V\'V����(��<�B�)Rc[T��S#Ӽ?�*���nm�Q�\'��:72	n�*3�e$CF��H2W���6Yt�ڄ������D�J��a|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Pyzm�6ި��W!���Z�O��}�i��et*��pMՕ�ͤ�6]���i|�j�6^����̕k���n�2�Q$c#S[,WePya��d�Jv]ɾ+y]�B�]q|���Q�![e�@㛮b�T�LG�ϴ���Pљ��N;<Kd�������v�}���w.����Vz&7>��>E�������'),(100,100,800,600,'x���?nA������\Zb\Z�v�\n)M���R�L���D���ggM�D�\r((�(�\0=�@����B�ߑ>;��{�{��V� G����5�m\\T�����񙭏���7�2�Q$�y��j]��ݺ�]�$2���F�0��k��s��F�L�[��,�2̐av&sU�4l͢����:�����|����k$��\"/��s\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�&��k���Z}��n�h��ד�?헱�Nd������r���߶�zZ:��\\���E��<�\\U8\r�x�*�\ZE2Ndj��\n*ϒ��]f�e�]�U�K�w�U�/��y��*�{\Z_w��f:��L����ʨ�l�g���}j��Gw���ӷ����˧+糕����Ou�O�t���è�'),(100,100,800,700,'x��ϿnA����qB�D\r1\rtw�\n)M���]\Z6��y�����9S ��tT�(}z	��;_,DA����V������_r�9*�Ɨ�Qo�J]���l}l��9�a�\"_��,T�25v�Jtqbd��?�ʡ�������΍L�[���tɐ��2��U�ӰM]ż�ס�����盟߾D\"m-��>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00h�<����������Fۏ?�|����2:�I�߻����z�ۮ�i�4�t�zU��8sU�4l�[�k���Ԗ�UT���+��]W��J^U��yW\\���|�f�V��������u7���g���w��TFmg;<Kd���w�\'?�;�ݝ����~�.����Vz*�>��>E���飩N'),(100,100,800,800,'x���?n�@���)5di���R�D-ti��Gމ���ۍM��1((�QQrz�����X����;�gF#�7�7_~�Q�p\Z]�Z���2qY��*��թU{��R�5���J������*ӫ3#����*����m�S���׺22n�*K�e�CF|ȈSW�NC���US�B��ŷ���?��ME�J��q|	\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Py|c�6کϢ�!���Z��?�|�n?�dr&�ڿwMՕ��ߛ��i�4�v�z�e�(ue�4���mT�5������mT���+��CW<vůJ_�+.�O}�N>�3d�,�i|�U�������3^�;��\\&M��%2[�g�\'?�s[���?�G������t��rt�ݎ)���oz=�'),(100,100,800,900,'x���?nA���?qB�D\r1\rt�p��&�FA�.\r�h=��c��٥@B��tT\\��c qf��9�w�όFzo~o������Ii5���:Uj�0uta�S���եk4�ɕ��|�)��t֫�������Q9Nn���uy��F�L�]��<	��2��V�U���P�h��_���������c��yv�/\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\Z/o��Duy��{�%�����G�O�ɴqom[�r���{��V�kۨ3QQ�6�2[V}-�JeX��L��r���ʓ$t�CW��]�ʕ�ŕw�+��;���*�;\Z_��E[��N�?����C��e���-��r~k����|~�����<>[[���\\�n]��C�������'),(100,100,900,0,'x��Ͽn�@��9��\nR$jHh��%ګ��$�((Х���m���.>\n$$^\"%/@�cD���wB�	�+}ly=��ٯ�e�	rP:��\\���E��<.l���Ȫ=z{&�\ZE2���Y��ej�������4�?\Z�}smW6Y�����7W�C�e$CF��H2WN�:9�*fm��i�?.���}�D�Z��~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`�y|mW6^���W!ص�=��i?�et,�ƿwmݕ�ͤ�]���i|�\Z�6.���q��iX��n�2�Q$c#S[�UPyf��d�J6]ɮ+y]�Rg]q|��w�A�![���7]Ŭ����?����CE\'2j;��Z\"��۟/�>��v��vW�q?�?�O�g=����b�\"����/��'),(100,100,900,100,'x���?n�@���)5$4���\n)M���B�4L�#�D����Ʀ@B�H)(�RPpz����׻B�ߑ>�<~o~o����:�A�4�r�z�e�(�Utj�#����L�5\Z��R��\\���*��c#����U�͵]�x�>���und\Z�BUM��&#N]�;\rm|�Ưj��N��y��u�e,�T\"/���3\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�:��k���J}������|������<�ѱLk��5UW.7���t]O�ѕ���(/�E�+s�����BeX��L��l�X�A���+�tŻ��U��w�e�/��{y`�l��{\Z_w��f:��L���5>�Q�i�k�L�m~���G�ݏ�����#�s�l�|��ٻ�.w)���\0��b'),(100,100,900,200,'x���?n�@���)5$4Ѝ�R�D��]\Z&�w\"�c�vcS !q	\nJ:*��\0��Pa�w�(�	�#}ly����|�%{u��©�t�zk�2q��meNmud��:�a��2���i(WE�����q,����U��+���J}ž�y,���rwѐm2�ԕ���F�]ż�V�?������ϯ��\"M%�l�>\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\00��<��kkV�3�<�ƾ����G��O˴�o]Su�r=�M���pj.]�ޚ�L\\fRW�NCk��BeX��Lb��b�,�ʓ�늆�h���/t�����x#��^<d�������7U7���gڜ�w�񉌚N;\\Kdr�����lw?lw��}�~��-�O�z\"{�>��.E���>��*'),(100,100,900,300,'x���?n�@���� �HԐ�@��l��&�DA!]$�d=ڝ�����HH\\����*��H�{�+DAN��c�������o٫�N�+W��q^�.�s[ŧ�:�j�.�dX�HƗr�U�\Zۺ�]�f�G��o���&+�Yb|�3#���rh��d�H6����&�]Ŭ�V�?������_�_\"��y���\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�A^۵�W��E�5�����G�~��cˤ��\\Su�r3�M���p\Z_�Z���2uY�pe�4�񹛫k�����eT���+��MW��J^���YW\\���[y/�̐�rxG㫮b�T�L��is�ߡ�5�v��������O~�����}~����9|�t~��ٻ�.w)���\0ω��'),(100,100,900,400,'x��Ͻn�@��?���\Z\Z���R�D��]\Z&�w\"�1>��)���\0Z\nJ:�)(i�$�{�+DA�����9���[�� �Ss�j���e�2��ʜ��Ȫ=zu&�\Z�er!��P��$��*��XfY�Q���Wvm���,�}��X��-T�0�2�!#�dD�+s���λ�yS�B���O׿n��E�J��~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`Pyxe�֬�g�y��}�����>���\'2:�i�ߺ����z�?���q��\\�Z�5y��̤�̝�֜��ʰFc��2��bY�\'q�\r]Ѧ+�uE/J_�+.�O}�F�ɽx�V9���eW1o�n���ϴ9��P�5�v����v��{�\'?��~��~�����>[:�.�D��}��]�$��x��'),(100,100,900,500,'x���?n�@���� �HԐЄ�>�VHiM��0Y�����	�KPP�QQPpz��D���Y!\nr��H��zo~o>���&�A�4�r�zU��u|f�c���幌k��R�g�Z����*�剑y>\Z�}sm76Y���]��T�C�g$cF��H2WNC�\\���^�ᶳo��6?�|�D�Z��~|\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0`�yxm76^���g!���F�w?}�n?Ner\"�ƿqmݗ��l��}���i|�\Z�6.���q��i���T�$����-��*�<1}W2v%ۮdו<�|����\n>��ky+�̘�rxG㋾b���L��i{�ߡ�S���n|����GG͐�����ێ�w�x���+糕��ލOu�K�t������'),(100,100,900,600,'x���?n�@���� �HԐ�@�9�VHi9\n\nHi��Gމ���ۍM��D�\r((�((�\0=�@���9�w��G�7�7_~�^�t\Z_�F���*uy\\�:>���U{��L�5�dz!w�P��4��*��q\"�|�iT��+��f�>7�ot��,���&}�3�6�d�*��Μ���^����o~}����ȓ�>�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0��	r��nl�V��OC�]�m��|���4�ɱ�\Z�Ƶu_.׳���]K��k�۸�R�Ǚ�\n�����Re\\�H���m�\\UA�Q�w���l�̮�<�|����\n>��ky+w�1[����}Ţ����?����CE\'2i{��,��q�>4C���7�/o�0�?��W�g+=��k��j�\"�n���N'),(100,100,900,700,'x��Ͽn�@��9��\nR$jHh�[?�UHi9\n\nti؜W������Ŧ@B�%((�((==��π}�	Q�\'����ʫ���~�%{M��ҩ�t�zk�*u�)lmNm}d��8�qM\"�^��,T�2Ml�Jtq��<~\Z����nl�V�ǉot��,���&}F<f�ی8sU�4t�y_�h�un;������珑H[�<���\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�� ���ƚ���<\r�v�o�}�������T&�2k�k��}�\\φo�w=,��Kר���R���U��Йs�T�$�i\"s[.WUPy��]��o��]W���.��*�̗���I�l��[\Z�����g:��L����Ȥ�u�D�_�=����nN�ݜ~�����\\>_9���D��}��]����7Ҫ'),(100,100,900,800,'x���?nA���?qB�D\r1\rt�p��&�FA�.\r�h=��c��٥@B��tT\\��c qf��9�w�όFzo~o������Ii5���:Uj�0uta�S���եk4�ɕ��|�)��t֫�������Q9Nn���uy��F�L�]��<	��2��V�U���P�h��_���������c��yv�/\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\Z/o��Duy��{�%�����G�O�ɴqom[�r���{��V�kۨ3QQ�6�2[V}-�JeX��L��r���ʓ$t�CW��]�ʕ�ŕw�+��;���*�;\Z_��E[��N�?����C��e���-�ɷ��l���ۇ�9�����<>[[���\\�n]��C�����]��'),(100,100,900,900,'x���?n�@���)5$4���\n)M���B�4L�#�D����Ʀ@B�)�(Rpz�����X����;�gF#�7�7_~�^�p\Z]�Z���2qY��*:�ՑU{tq&ÚLev)�Pn������屑E�_j�}sm�6ި�b�k]\Z��R�C�e�CF�ˈSW�NC�w˦ڄ���oo�o~�<i*��]�\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0�A����h�>�^�`[�km>�x�黽���X�皪+��y�7]���it�j�6���eQ���ih�s�R�d*3#[��ePyf��x�w]���*}�ˮ�>��[y/̐�rxO��b�T�L��i��ߡ�\'2i:��-���x�}�?���y����y|�v>]����D�c�$��L��');
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
