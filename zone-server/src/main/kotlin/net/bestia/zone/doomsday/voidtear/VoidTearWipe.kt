package net.bestia.zone.doomsday.voidtear

import net.bestia.zone.doomsday.persistence.StoredVoidTearRepository
import net.bestia.zone.world.WorldScopedData
import org.springframework.stereotype.Component

/** A new world has no Tear yet. */
@Component
class VoidTearWipe(
  private val tears: StoredVoidTearRepository,
) : WorldScopedData {

  override fun wipe() {
    tears.deleteAll()
  }
}
