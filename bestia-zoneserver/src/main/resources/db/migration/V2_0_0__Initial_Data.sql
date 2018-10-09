LOCK TABLES `attacks` WRITE;
/*!40000 ALTER TABLE `attacks` DISABLE KEYS */;
INSERT INTO `attacks`
VALUES
(1,'tackle','NORMAL',300,0,'tackle','NORMAL','null','',5,1,10,'ENEMY_ENTITY','\0',NULL);
/*!40000 ALTER TABLE `attacks` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `bestias` WRITE;
/*!40000 ALTER TABLE `bestias` DISABLE KEYS */;
INSERT INTO `bestias`
VALUES
(1,'blob','Poring','NORMAL',10,'poring','\0',1,NULL,'poring','PACK','FORMLESS',1,1,1,1,1,1,1,1,'PACK'),
(2,'mastersmith','Master','NORMAL',1000,'master_1','\0',1,NULL,'mastersmith','DYNAMIC','HUMANOID',5,5,5,5,5,5,5,5,'DYNAMIC');
/*!40000 ALTER TABLE `bestias` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `items` WRITE;
/*!40000 ALTER TABLE `items` DISABLE KEYS */;
INSERT INTO `items` VALUES
(1,'apple',NULL,'apple',1,'USABLE',0,1,0,0);
/*!40000 ALTER TABLE `items` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;