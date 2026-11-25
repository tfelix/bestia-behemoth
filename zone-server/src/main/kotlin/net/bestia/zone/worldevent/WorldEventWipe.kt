package net.bestia.zone.worldevent

import net.bestia.zone.world.WorldScopedData
import net.bestia.zone.worldevent.persistence.EventProvinceRepository
import net.bestia.zone.worldevent.persistence.EventContributionRepository
import net.bestia.zone.worldevent.persistence.StoredRiftRepository
import net.bestia.zone.worldevent.persistence.StoredSealStoneRepository
import net.bestia.zone.worldevent.persistence.WorldEventInstanceRepository
import org.springframework.stereotype.Component

/** The event state of a world that is being replaced. */
@Component
class WorldEventWipe(
  private val provinces: EventProvinceRepository,
  private val instances: WorldEventInstanceRepository,
  private val rifts: StoredRiftRepository,
  private val contributions: EventContributionRepository,
  private val sealStones: StoredSealStoneRepository,
) : WorldScopedData {

  override fun wipe() {
    sealStones.deleteAll()
    contributions.deleteAll()
    rifts.deleteAll()
    instances.deleteAll()
    provinces.deleteAll()
  }
}
