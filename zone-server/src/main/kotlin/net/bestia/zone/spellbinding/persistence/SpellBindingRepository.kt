package net.bestia.zone.spellbinding.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SpellBindingRepository : JpaRepository<SpellBindingRow, Long> {

  fun findAllByMasterIdOrderByBoundAt(masterId: Long): List<SpellBindingRow>

  @Query("select coalesce(max(b.id), 0) from SpellBindingRow b")
  fun maxId(): Long

  fun findAllByItemInstanceIdIsNull(): List<SpellBindingRow>

  fun findByItemInstanceId(itemInstanceId: Long): SpellBindingRow?
}
