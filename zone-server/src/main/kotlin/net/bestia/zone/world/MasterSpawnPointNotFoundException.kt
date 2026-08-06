package net.bestia.zone.world

class MasterSpawnPointNotFoundException(id: Long) :
  RuntimeException("No MasterSpawnPoint with id $id")