package net.bestia.zone.prop.persistence

import jakarta.persistence.LockModeType
import net.bestia.zone.entity.StaticEntityKind
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PlayerStructureRepository : JpaRepository<PlayerStructure, Long> {

  fun findAllByOwnerAccountIdIsNull(): List<PlayerStructure>

  fun findAllByKind(kind: StaticEntityKind): List<PlayerStructure>

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from PlayerStructure s where s.id = :id")
  fun findByIdForUpdate(@Param("id") id: Long): PlayerStructure?
}
