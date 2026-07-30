package net.bestia.zone.world

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
interface MasterSpawnPointRepository : JpaRepository<MasterSpawnPoint, Long>

fun MasterSpawnPointRepository.findByIdOrThrow(id: Long): MasterSpawnPoint {
  return findByIdOrNull(id) ?: throw MasterSpawnPointNotFoundException(id)
}

class MasterSpawnPointNotFoundException(id: Long) :
  RuntimeException("No MasterSpawnPoint with id $id")
