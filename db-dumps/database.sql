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
-- Table structure for table `account`
--

DROP TABLE IF EXISTS `account`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `account` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `additionalBestiaSlots` int(11) NOT NULL,
  `bannedUntilDate` datetime DEFAULT NULL,
  `email` varchar(32) DEFAULT NULL,
  `gold` int(11) NOT NULL,
  `isActivated` bit(1) NOT NULL,
  `lastLogin` datetime DEFAULT NULL,
  `loginToken` varchar(255) DEFAULT NULL,
  `password` tinyblob,
  `registerDate` datetime DEFAULT NULL,
  `remarks` varchar(255) DEFAULT NULL,
  `userLevel` int(11) DEFAULT NULL,
  `MASTER_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_cs5bnaggwuluahrdh8mbs1rpe` (`email`),
  KEY `FK_tpg2joyivqno398nreujh65kg` (`MASTER_ID`),
  CONSTRAINT `FK_tpg2joyivqno398nreujh65kg` FOREIGN KEY (`MASTER_ID`) REFERENCES `player_bestias` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `account`
--

LOCK TABLES `account` WRITE;
/*!40000 ALTER TABLE `account` DISABLE KEYS */;
INSERT INTO `account` VALUES (1,0,NULL,'test@test.de',1,'','2015-07-14 22:46:29','0596cb1b-ce63-4feb-a524-baf6c3a15b71','?',NULL,' ',1,2),(2,0,NULL,'test2@test.de',2,'','2015-07-26 14:48:27','c4145215-7fb1-45d6-ab8c-bcaac5347949','—ôpŸèœÏƒ_≈eß&H8î)Óü¶tG‰$lÃ’ê s',NULL,NULL,1,3);
/*!40000 ALTER TABLE `account` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `attack`
--

DROP TABLE IF EXISTS `attack`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `attack` (
  `databaseName` varchar(255) NOT NULL,
  `basedStatus` varchar(255) DEFAULT NULL,
  `element` varchar(255) DEFAULT NULL,
  `manaCost` int(11) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `strength` int(11) NOT NULL,
  PRIMARY KEY (`databaseName`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `attack`
--

LOCK TABLES `attack` WRITE;
/*!40000 ALTER TABLE `attack` DISABLE KEYS */;
/*!40000 ALTER TABLE `attack` ENABLE KEYS */;
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
INSERT INTO `bestias` VALUES (1,1,1,1,1,1,1,1,'doom','NORMAL',10,0,'jh','\0',1,' ','mastersmith');
/*!40000 ALTER TABLE `bestias` ENABLE KEYS */;
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
  PRIMARY KEY (`id`),
  KEY `FK_81t47rhhlhgauym19gt8o4yc3` (`BESTIA_ID`),
  KEY `FK_21sb2x6oo6obaxo6o1xg9217y` (`ACCOUNT_ID`),
  CONSTRAINT `FK_21sb2x6oo6obaxo6o1xg9217y` FOREIGN KEY (`ACCOUNT_ID`) REFERENCES `account` (`id`),
  CONSTRAINT `FK_81t47rhhlhgauym19gt8o4yc3` FOREIGN KEY (`BESTIA_ID`) REFERENCES `bestias` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `player_bestias`
--

LOCK TABLES `player_bestias` WRITE;
/*!40000 ALTER TABLE `player_bestias` DISABLE KEYS */;
INSERT INTO `player_bestias` VALUES (1,'test-zone1',5,5,1,1,1,1,11,1,1,1,1,1,1,1,1,1,1,1,'blubber','test-zone1',0,0,1,1,5,5),(2,'test-zone1',21,10,1,1,1,100,11,1,1,1,1,1,1,1,1,1,1,5,'blubber 2','test-zone1',0,0,1,1,10,9),(3,'test-zone1',12,8,1,1,1,100,11,1,1,1,1,1,1,1,1,1,1,5,'blubber 3','test-zone1',0,0,1,2,10,9);
/*!40000 ALTER TABLE `player_bestias` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2015-07-27 19:43:35
