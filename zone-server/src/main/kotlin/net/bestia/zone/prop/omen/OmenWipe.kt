package net.bestia.zone.prop.omen

import net.bestia.zone.prop.persistence.OmenScarRepository
import net.bestia.zone.world.WorldScopedData
import org.springframework.stereotype.Component

/** The scars of a world that is being replaced. */
@Component
class OmenWipe(
  private val repository: OmenScarRepository,
) : WorldScopedData {

  override fun wipe() {
    repository.deleteAll()
  }
}
