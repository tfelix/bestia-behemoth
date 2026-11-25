package net.bestia.zone.worldevent

import net.bestia.zone.world.WorldScopedData
import net.bestia.zone.worldevent.persistence.EventProvinceRepository
import net.bestia.zone.worldevent.persistence.WorldEventInstanceRepository
import org.springframework.stereotype.Component

/** The event state of a world that is being replaced. */
@Component
class WorldEventWipe(
  private val provinces: EventProvinceRepository,
  private val instances: WorldEventInstanceRepository,
) : WorldScopedData {

  override fun wipe() {
    instances.deleteAll()
    provinces.deleteAll()
  }
}
