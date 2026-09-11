package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.world.prop.ConstructionSiteSpawner
import net.bestia.zone.world.prop.PlayerStructureRegistry
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Puts every half-built structure back into the world.
 *
 * Right after [PlayerStructureBootRunner] (`@Order(4)`), which is what fills the registry this reads.
 *
 * Everything standing is materialised lazily instead, per chunk column, as players walk up to it - because
 * there are potentially very many of them and they are cheap static props. A site is neither: they are rare,
 * and each is an ordinary entity that has to exist to be findable at all. Spawning the handful of them once
 * is simpler than a second residency path, and is what lets a player log back in next to the site they left.
 */
@Component
@Order(5)
class ConstructionSiteBootRunner(
  private val structures: PlayerStructureRegistry,
  private val sites: ConstructionSiteSpawner,
  private val world: WorldView
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    val pending = structures.underConstruction()
    pending.forEach { entry -> sites.spawn(world, entry) }

    if (pending.isNotEmpty()) {
      LOG.info { "Restored ${pending.size} construction site(s)" }
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
