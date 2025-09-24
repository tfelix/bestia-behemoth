package net.bestia.zone.bestia

import net.bestia.zone.util.PlayerBestiaId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
interface PlayerBestiaRepository : JpaRepository<PlayerBestia, Long> {

  fun findAllByMasterId(masterId: Long): List<PlayerBestia>
}

fun PlayerBestiaRepository.findByIdOrThrow(id: PlayerBestiaId): PlayerBestia {
  return findByIdOrNull(id) ?: throw PlayerBestiaNotFoundException(id)
}