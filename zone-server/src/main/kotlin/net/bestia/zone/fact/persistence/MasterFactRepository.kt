package net.bestia.zone.fact.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface MasterFactRepository : JpaRepository<MasterFact, MasterFact.Key> {

  fun findAllByIdMasterId(masterId: Long): List<MasterFact>

  fun deleteAllByIdMasterId(masterId: Long)
}
