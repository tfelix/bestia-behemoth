package net.bestia.zone.account.persistence

import jakarta.persistence.LockModeType
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import net.bestia.zone.account.PlayerBestiaNotFoundException

@Repository
interface PlayerBestiaRepository : JpaRepository<PlayerBestia, Long> {

  fun findAllByMasterId(masterId: Long): List<PlayerBestia>

  /** Locks the row for a read-modify-write, like `MasterRepository.findByIdForUpdate`. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from PlayerBestia b where b.id = :id")
  fun findByIdForUpdate(@Param("id") id: PlayerBestiaId): PlayerBestia?
}

fun PlayerBestiaRepository.findByIdOrThrow(id: PlayerBestiaId): PlayerBestia {
  return findByIdOrNull(id) ?: throw PlayerBestiaNotFoundException(id)
}
