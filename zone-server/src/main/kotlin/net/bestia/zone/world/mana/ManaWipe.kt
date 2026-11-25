package net.bestia.zone.world.mana

import net.bestia.zone.world.WorldScopedData
import net.bestia.zone.world.persistence.StoredManaTileRepository
import org.springframework.stereotype.Component

/** The mana tiles of a world that is being replaced. */
@Component
class ManaWipe(
  private val repository: StoredManaTileRepository,
) : WorldScopedData {

  override fun wipe() {
    repository.deleteAll()
  }
}
