package net.bestia.zone.doomsday

import net.bestia.zone.doomsday.persistence.StoredDoomsdayRepository
import net.bestia.zone.world.WorldScopedData
import org.springframework.stereotype.Component

/** A new world gets a new fate. */
@Component
class DoomsdayWipe(
  private val doomsdays: StoredDoomsdayRepository,
) : WorldScopedData {

  override fun wipe() {
    doomsdays.deleteAll()
  }
}
