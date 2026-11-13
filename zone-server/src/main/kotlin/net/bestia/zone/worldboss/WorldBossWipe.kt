package net.bestia.zone.worldboss

import net.bestia.zone.world.WorldScopedData
import net.bestia.zone.worldboss.persistence.SlainWorldBossRepository
import org.springframework.stereotype.Component

/** A new world has new lairs, and its bosses are all alive. */
@Component
class WorldBossWipe(
  private val repository: SlainWorldBossRepository,
) : WorldScopedData {

  override fun wipe() {
    repository.deleteAll()
  }
}
