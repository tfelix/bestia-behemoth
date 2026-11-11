package net.bestia.zone.account.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface OrderPresetRepository : JpaRepository<OrderPreset, Long> {

  fun findAllByMasterIdOrderByName(masterId: Long): List<OrderPreset>

  fun findByIdAndMasterId(id: Long, masterId: Long): OrderPreset?

  fun existsByMasterIdAndName(masterId: Long, name: String): Boolean

  fun countByMasterId(masterId: Long): Long

  fun deleteAllByMasterId(masterId: Long)
}
