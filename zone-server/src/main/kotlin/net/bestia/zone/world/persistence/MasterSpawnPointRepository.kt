package net.bestia.zone.world.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import net.bestia.zone.world.MasterSpawnPointNotFoundException

@Repository
interface MasterSpawnPointRepository : JpaRepository<MasterSpawnPoint, Long>

fun MasterSpawnPointRepository.findByIdOrThrow(id: Long): MasterSpawnPoint {
  return findByIdOrNull(id) ?: throw MasterSpawnPointNotFoundException(id)
}

