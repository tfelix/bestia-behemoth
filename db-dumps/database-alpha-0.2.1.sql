CREATE DATABASE  IF NOT EXISTS `bestia2` /*!40100 DEFAULT CHARACTER SET latin1 */;
USE `bestia2`;
-- MySQL dump 10.13  Distrib 5.6.17, for Win32 (x86)
--
-- Host: 127.0.0.1    Database: bestia2
-- ------------------------------------------------------
-- Server version	5.5.34

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
  `additionalBestiaSlots` int(11) NOT NULL,
  `bannedUntilDate` datetime DEFAULT NULL,
  `email` varchar(32) DEFAULT NULL,
  `gold` int(11) NOT NULL,
  `isActivated` bit(1) NOT NULL,
  `lastLogin` datetime DEFAULT NULL,
  `loginToken` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `registerDate` datetime DEFAULT NULL,
  `remarks` varchar(255) DEFAULT NULL,
  `userLevel` int(11) DEFAULT NULL,
  `MASTER_ID` int(11) DEFAULT NULL,
  `language` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_cs5bnaggwuluahrdh8mbs1rpe` (`email`),
  KEY `FK_tpg2joyivqno398nreujh65kg` (`MASTER_ID`),
  CONSTRAINT `FK_tpg2joyivqno398nreujh65kg` FOREIGN KEY (`MASTER_ID`) REFERENCES `player_bestias` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `accounts`
--

LOCK TABLES `accounts` WRITE;
/*!40000 ALTER TABLE `accounts` DISABLE KEYS */;
INSERT INTO `accounts` VALUES (1,0,NULL,'test@test.de',1,'','2015-10-12 00:00:00','3b7a6867-e0a8-42f3-a863-b5eee014b44c','z4NivVn/lr5C7hwzhn7kq7INsuPZ20eogHFSqb+b03mZIa+m2YzOCfOsWm5dJC/NzGXomkFQjpz+vgMHhnDEsNMm1eWDbykjmD5cq8Jo8lCCM7sXUzobu1Qc49xX2RmQpIgWr05WNh/GEWkmINYgWhGP+m/mauaTnfh05tRr9Wg=$YCF+Rx68/Sd69WzMSFCXeqGG+mIpq9Q3ySiDz/xg4YQ=',NULL,' ',1,2,'de-DE'),(2,0,NULL,'test2@test.de',2,'','2015-10-20 00:00:00','0520a41c-3065-4704-9cac-53c30aca8f9f','OFLFag6RDyDVbh/ernOQJ7g+c9eXYpOa0LjRc98ecJBruzqLYBAWFvQA2naDYh94YTgqmdD7FW5QShN7H5x7doy1UnGD9BSz98btM8OP8J3GRMgbi2GOlzuucUWf0UPbdlBmK6179zK60OOvXBwYiKOs+Q7y38bc+HG9UtVs5ns=$YWfKbEdd8rLheHaF3PYYD3IWR2RtK1UHDsGE95ylcRE=',NULL,NULL,1,3,'de-DE');
/*!40000 ALTER TABLE `accounts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `attacks`
--

DROP TABLE IF EXISTS `attacks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `attacks` (
  `id` int(11) NOT NULL,
  `basedStatus` varchar(255) DEFAULT NULL,
  `attack_db_name` varchar(255) NOT NULL,
  `element` varchar(255) DEFAULT NULL,
  `manaCost` int(11) NOT NULL,
  `strength` int(11) NOT NULL,
  `casttime` int(11) NOT NULL,
  `cooldown` int(11) NOT NULL,
  `range` int(11) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_tigj3iera7lvpgs98piy9b9g9` (`attack_db_name`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `attacks`
--

LOCK TABLES `attacks` WRITE;
/*!40000 ALTER TABLE `attacks` DISABLE KEYS */;
INSERT INTO `attacks` VALUES (1,'NORMAL','tackle','NORMAL',2,5,500,1000,1),(2,'SPECIAL','ember','FIRE',4,8,500,1000,1);
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
  `minLevel` int(11) NOT NULL,
  `ATTACK_ID` int(11) NOT NULL,
  `BESTIA_ID` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_rul2cv5tx3hk5r0f0fjc0coy1` (`ATTACK_ID`,`BESTIA_ID`),
  KEY `FK_8vb5ut9e3d9dnd1mueuhrnjt` (`BESTIA_ID`),
  CONSTRAINT `FK_6f6kkks31em9dught810v6s7p` FOREIGN KEY (`ATTACK_ID`) REFERENCES `attacks` (`id`),
  CONSTRAINT `FK_8vb5ut9e3d9dnd1mueuhrnjt` FOREIGN KEY (`BESTIA_ID`) REFERENCES `bestias` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bestia_attacks`
--

LOCK TABLES `bestia_attacks` WRITE;
/*!40000 ALTER TABLE `bestia_attacks` DISABLE KEYS */;
INSERT INTO `bestia_attacks` VALUES (1,1,1,1),(2,8,2,1);
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
  `bAtk` int(11) DEFAULT NULL,
  `bDef` int(11) DEFAULT NULL,
  `bHp` int(11) DEFAULT NULL,
  `bMana` int(11) DEFAULT NULL,
  `bSpAtk` int(11) DEFAULT NULL,
  `bSpDef` int(11) DEFAULT NULL,
  `bSpd` int(11) DEFAULT NULL,
  `bestia_db_name` varchar(255) DEFAULT NULL,
  `element` varchar(255) DEFAULT NULL,
  `expGained` int(11) NOT NULL,
  `gold` int(11) NOT NULL,
  `image` varchar(255) DEFAULT NULL,
  `isBoss` bit(1) NOT NULL,
  `level` int(11) NOT NULL,
  `scriptExec` varchar(255) DEFAULT NULL,
  `sprite` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bestias`
--

LOCK TABLES `bestias` WRITE;
/*!40000 ALTER TABLE `bestias` DISABLE KEYS */;
INSERT INTO `bestias` VALUES (1,1,1,1,1,1,1,1,'doom','NORMAL',10,0,'jh','\0',1,' ','mastersmith'),(2,1,1,1,1,1,1,1,'poring','NORMAL',10,5,'poring.png','\0',1,NULL,'poring');
/*!40000 ALTER TABLE `bestias` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `drop_items`
--

DROP TABLE IF EXISTS `drop_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `drop_items` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `probability` float NOT NULL,
  `BESTIA_ID` int(11) NOT NULL,
  `ITEM_ID` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_96a2o9nnkbm1udiixpplqnkn7` (`BESTIA_ID`),
  KEY `FK_lpp22kh3grpy8rjdgplyeqf4` (`ITEM_ID`),
  CONSTRAINT `FK_lpp22kh3grpy8rjdgplyeqf4` FOREIGN KEY (`ITEM_ID`) REFERENCES `items` (`id`),
  CONSTRAINT `FK_96a2o9nnkbm1udiixpplqnkn7` FOREIGN KEY (`BESTIA_ID`) REFERENCES `bestias` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `drop_items`
--

LOCK TABLES `drop_items` WRITE;
/*!40000 ALTER TABLE `drop_items` DISABLE KEYS */;
/*!40000 ALTER TABLE `drop_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `guild_player`
--

DROP TABLE IF EXISTS `guild_player`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `guild_player` (
  `guildId` int(11) NOT NULL AUTO_INCREMENT,
  `expEarned` int(11) NOT NULL,
  `ACCOUNT_ID` bigint(20) NOT NULL,
  `GUILD_ID` int(11) NOT NULL,
  PRIMARY KEY (`guildId`),
  UNIQUE KEY `UK_6hrq2cjp2absddcnk236iemp7` (`GUILD_ID`,`ACCOUNT_ID`),
  KEY `FK_ccime4aq1oj7fk42cl9722oyr` (`ACCOUNT_ID`),
  CONSTRAINT `FK_48j9f3jslg99qn6hxia6twv5r` FOREIGN KEY (`GUILD_ID`) REFERENCES `guilds` (`id`),
  CONSTRAINT `FK_ccime4aq1oj7fk42cl9722oyr` FOREIGN KEY (`ACCOUNT_ID`) REFERENCES `accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `guild_player`
--

LOCK TABLES `guild_player` WRITE;
/*!40000 ALTER TABLE `guild_player` DISABLE KEYS */;
/*!40000 ALTER TABLE `guild_player` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `guilds`
--

DROP TABLE IF EXISTS `guilds`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `guilds` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `emblem` varchar(255) DEFAULT NULL,
  `level` int(11) NOT NULL,
  `name` varchar(40) NOT NULL,
  `leader_guildId` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_jdl3u9rp7vaqkdowxq64ddhso` (`name`),
  UNIQUE KEY `UK_6nd6rhif60amh1gvykjiw1u1n` (`leader_guildId`),
  CONSTRAINT `FK_6nd6rhif60amh1gvykjiw1u1n` FOREIGN KEY (`leader_guildId`) REFERENCES `guild_player` (`guildId`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
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
  `category` varchar(255) NOT NULL,
  `translation_key` varchar(255) NOT NULL,
  `lang` varchar(5) NOT NULL,
  `value` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`category`,`translation_key`,`lang`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `i18ns`
--

LOCK TABLES `i18ns` WRITE;
/*!40000 ALTER TABLE `i18ns` DISABLE KEYS */;
INSERT INTO `i18ns` VALUES ('ITEM','apple','de-DE','Apfel'),('ITEM','apple_desc','de-DE','Ein gewöhnlicher, roter Apfel. Er sieht saftig aus und wird einer Bestia sicher schmecken.'),('ITEM','empty_bottle','de-DE','Leere Flasche'),('ITEM','empty_bottle_desc','de-DE','Eine Flasche aus transparentem Glas. Sie ist leer. Eignet sich sicher als Gefäß für Flüssigkeiten.');
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
  `image` varchar(255) NOT NULL,
  `item_db_name` varchar(255) NOT NULL,
  `price` int(11) NOT NULL,
  `type` varchar(255) DEFAULT NULL,
  `weight` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_8gg9gkwuvj66l7r2jr3i90xyy` (`item_db_name`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `items`
--

LOCK TABLES `items` WRITE;
/*!40000 ALTER TABLE `items` DISABLE KEYS */;
INSERT INTO `items` VALUES (1,'apple.png','apple',2,'USABLE',1),(2,'empty_bottle.png','empty_bottle',2,'ETC',1);
/*!40000 ALTER TABLE `items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `player_bestias`
--

DROP TABLE IF EXISTS `player_bestias`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `player_bestias` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `mapDbName` varchar(255) DEFAULT NULL,
  `x` int(11) NOT NULL,
  `y` int(11) NOT NULL,
  `evAtk` int(11) DEFAULT NULL,
  `evDef` int(11) DEFAULT NULL,
  `evHp` int(11) DEFAULT NULL,
  `evMana` int(11) DEFAULT NULL,
  `evSpAtk` int(11) DEFAULT NULL,
  `evSpDef` int(11) DEFAULT NULL,
  `evSpd` int(11) DEFAULT NULL,
  `exp` int(11) NOT NULL,
  `ivAtk` int(11) DEFAULT NULL,
  `ivDef` int(11) DEFAULT NULL,
  `ivHp` int(11) DEFAULT NULL,
  `ivMana` int(11) DEFAULT NULL,
  `ivSpAtk` int(11) DEFAULT NULL,
  `ivSpDef` int(11) DEFAULT NULL,
  `ivSpd` int(11) DEFAULT NULL,
  `level` int(11) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `saveMapDbName` varchar(255) DEFAULT NULL,
  `saveX` int(11) DEFAULT NULL,
  `saveY` int(11) DEFAULT NULL,
  `BESTIA_ID` int(11) NOT NULL,
  `ACCOUNT_ID` bigint(20) NOT NULL,
  `currentHp` int(11) NOT NULL,
  `currentMana` int(11) NOT NULL,
  `ATTACK_1` int(11) DEFAULT NULL,
  `ATTACK_2` int(11) DEFAULT NULL,
  `ATTACK_3` int(11) DEFAULT NULL,
  `ATTACK_4` int(11) DEFAULT NULL,
  `ATTACK_5` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_81t47rhhlhgauym19gt8o4yc3` (`BESTIA_ID`),
  KEY `FK_21sb2x6oo6obaxo6o1xg9217y` (`ACCOUNT_ID`),
  KEY `FK_3o3usa8i2guwq5k9re2sxv0kb` (`ATTACK_1`),
  KEY `FK_q81kn8qyvm2syeunxuq452p6x` (`ATTACK_2`),
  KEY `FK_c26l0d2b0fycxujpfomcf1k7o` (`ATTACK_3`),
  KEY `FK_ecr5pio0wtoarfpjq6dnoa1gc` (`ATTACK_4`),
  KEY `FK_m40dma5gjl58igdd8rn43n3qf` (`ATTACK_5`),
  CONSTRAINT `FK_21sb2x6oo6obaxo6o1xg9217y` FOREIGN KEY (`ACCOUNT_ID`) REFERENCES `accounts` (`id`),
  CONSTRAINT `FK_3o3usa8i2guwq5k9re2sxv0kb` FOREIGN KEY (`ATTACK_1`) REFERENCES `attacks` (`id`),
  CONSTRAINT `FK_81t47rhhlhgauym19gt8o4yc3` FOREIGN KEY (`BESTIA_ID`) REFERENCES `bestias` (`id`),
  CONSTRAINT `FK_c26l0d2b0fycxujpfomcf1k7o` FOREIGN KEY (`ATTACK_3`) REFERENCES `attacks` (`id`),
  CONSTRAINT `FK_ecr5pio0wtoarfpjq6dnoa1gc` FOREIGN KEY (`ATTACK_4`) REFERENCES `attacks` (`id`),
  CONSTRAINT `FK_m40dma5gjl58igdd8rn43n3qf` FOREIGN KEY (`ATTACK_5`) REFERENCES `attacks` (`id`),
  CONSTRAINT `FK_q81kn8qyvm2syeunxuq452p6x` FOREIGN KEY (`ATTACK_2`) REFERENCES `attacks` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `player_bestias`
--

LOCK TABLES `player_bestias` WRITE;
/*!40000 ALTER TABLE `player_bestias` DISABLE KEYS */;
INSERT INTO `player_bestias` VALUES (1,'test-zone1',5,5,1,1,1,1,11,1,1,1,1,1,1,1,1,1,1,1,'blubber','test-zone1',0,0,1,1,5,5,1,2,NULL,NULL,NULL),(2,'test-zone1',10,10,1,1,1,100,11,1,1,1,1,1,1,1,1,1,1,5,'blubber 2','test-zone1',0,0,1,1,10,9,1,2,NULL,NULL,NULL),(3,'test-zone1',9,12,1,1,1,100,11,1,1,1,1,1,1,1,1,1,1,5,'blubber 3','test-zone1',0,0,1,2,10,9,1,2,NULL,NULL,NULL);
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
  `ACCOUNT_ID` bigint(20) DEFAULT NULL,
  `ITEM_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_gt740lb897ocnvahsoq0byqvp` (`ITEM_ID`,`ACCOUNT_ID`),
  KEY `FK_ip7v8qncoyaqddxyu4n4dkx4n` (`ACCOUNT_ID`),
  CONSTRAINT `FK_ht0qwtilwokwh29jjprjt4q51` FOREIGN KEY (`ITEM_ID`) REFERENCES `items` (`id`),
  CONSTRAINT `FK_ip7v8qncoyaqddxyu4n4dkx4n` FOREIGN KEY (`ACCOUNT_ID`) REFERENCES `accounts` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `player_items`
--

LOCK TABLES `player_items` WRITE;
/*!40000 ALTER TABLE `player_items` DISABLE KEYS */;
INSERT INTO `player_items` VALUES (1,2,1,1),(2,131,1,2),(3,5,2,1);
/*!40000 ALTER TABLE `player_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `statuseffect`
--

DROP TABLE IF EXISTS `statuseffect`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `statuseffect` (
  `id` int(11) NOT NULL,
  `armorMultMod` float NOT NULL,
  `armorSumMod` int(11) NOT NULL,
  `atkMultMod` float NOT NULL,
  `atkSumMod` int(11) NOT NULL,
  `datebaseName` varchar(255) DEFAULT NULL,
  `defMultMod` float NOT NULL,
  `defSumMod` int(11) NOT NULL,
  `spArmorMultMod` float NOT NULL,
  `spArmorSumMod` int(11) NOT NULL,
  `spAtkMultMod` float NOT NULL,
  `spAtkSumMod` int(11) NOT NULL,
  `spDefMultMod` float NOT NULL,
  `spDefSumMod` int(11) NOT NULL,
  `spdMultMod` float NOT NULL,
  `spdSumMod` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `statuseffect`
--

LOCK TABLES `statuseffect` WRITE;
/*!40000 ALTER TABLE `statuseffect` DISABLE KEYS */;
/*!40000 ALTER TABLE `statuseffect` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2015-10-26  0:32:18
