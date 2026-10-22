package net.bestia.zone.compendium

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.compendium.persistence.CompendiumEntry
import net.bestia.zone.compendium.persistence.CompendiumEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * The database half of the Bestia Compendium. Every write for a master runs on that master's account job, so
 * two writes for one species never race for the unique key.
 */
@Service
class CompendiumStore(
  private val entries: CompendiumEntryRepository,
  private val masters: MasterRepository,
  private val playerBestias: PlayerBestiaRepository,
  private val catalogue: BestiaCatalogue,
) {

  /**
   * The species [masterId] has seen. Species it owns count as caught, so a master that caught bestias before
   * the compendium existed does not start with an empty book.
   */
  @Transactional
  fun load(masterId: Long): Set<Long> {
    val ownedSpecies = playerBestias.findAllByMasterId(masterId).map { it.bestia.id }.toSet()
    ownedSpecies.forEach { recordCaught(masterId, it) }

    return entries.findAllByMasterId(masterId).mapTo(HashSet()) { it.speciesId }
  }

  /** True when this was the first sighting. */
  @Transactional
  fun recordSeen(masterId: Long, speciesId: Long): Boolean {
    if (!isListed(speciesId) || entries.findByMasterIdAndSpeciesId(masterId, speciesId) != null) {
      return false
    }

    entries.save(CompendiumEntry(masters.getReferenceById(masterId), speciesId, firstSeenAt = Instant.now()))

    return true
  }

  /** True when this was the first catch. A catch is also a sighting. */
  @Transactional
  fun recordCaught(masterId: Long, speciesId: Long): Boolean {
    if (!isListed(speciesId)) {
      return false
    }

    val now = Instant.now()
    val entry = entries.findByMasterIdAndSpeciesId(masterId, speciesId)
      ?: entries.save(CompendiumEntry(masters.getReferenceById(masterId), speciesId, firstSeenAt = now))

    if (entry.caughtAt != null) {
      return false
    }
    entry.caughtAt = now

    return true
  }

  /** Ordered by species number. Species that left the catalogue are skipped, their rows are kept. */
  @Transactional(readOnly = true)
  fun entries(masterId: Long): List<CompendiumEntry> {
    return entries.findAllByMasterId(masterId)
      .filter { isListed(it.speciesId) }
      .sortedBy { it.speciesId }
  }

  @Transactional(readOnly = true)
  fun hasSeen(masterId: Long, speciesId: Long): Boolean {
    return isListed(speciesId) && entries.findByMasterIdAndSpeciesId(masterId, speciesId) != null
  }

  @Transactional
  fun deleteAllOf(masterId: Long) {
    entries.deleteAllByMasterId(masterId)
  }

  private fun isListed(speciesId: Long): Boolean {
    return catalogue.find(speciesId)?.inCompendium == true
  }
}
