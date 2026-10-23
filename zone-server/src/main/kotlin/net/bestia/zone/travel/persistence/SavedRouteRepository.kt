package net.bestia.zone.travel.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface SavedRouteRepository : JpaRepository<SavedRoute, Long> {

  fun findAllByMasterIdOrderById(masterId: Long): List<SavedRoute>

  fun countByMasterId(masterId: Long): Long
}
