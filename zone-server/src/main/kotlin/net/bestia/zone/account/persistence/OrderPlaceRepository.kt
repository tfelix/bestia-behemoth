package net.bestia.zone.account.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface OrderPlaceRepository : JpaRepository<OrderPlace, Long> {

  fun findAllByMasterIdOrderByName(masterId: Long): List<OrderPlace>

  fun findByIdAndMasterId(id: Long, masterId: Long): OrderPlace?

  fun existsByMasterIdAndName(masterId: Long, name: String): Boolean

  fun countByMasterId(masterId: Long): Long

  fun deleteAllByMasterId(masterId: Long)
}
