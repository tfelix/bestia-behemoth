package net.bestia.zone.doomsday.destroyer

import net.bestia.zone.doomsday.persistence.StoredDestroyerRepository
import net.bestia.zone.world.WorldScopedData
import org.springframework.stereotype.Component

/** A new world has no Destroyer yet. */
@Component
class DestroyerWipe(
  private val rows: StoredDestroyerRepository,
) : WorldScopedData {

  override fun wipe() {
    rows.deleteAll()
  }
}
