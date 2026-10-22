package net.bestia.zone.compendium.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface CompendiumEntryRepository : JpaRepository<CompendiumEntry, Long> {

  fun findAllByMasterId(masterId: Long): List<CompendiumEntry>

  fun findByMasterIdAndSpeciesId(masterId: Long, speciesId: Long): CompendiumEntry?

  fun deleteAllByMasterId(masterId: Long)
}
