package net.bestia.zone.world

import net.bestia.worldgen.core.StageListener
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pipeline.WorldParams

/** How [WorldService] gets its world tier. A seam so the tests can share one world between their Spring contexts. */
fun interface WorldBuilder {

  fun build(config: WorldConfig, listener: StageListener, params: WorldParams): GeneratedWorld

  companion object {
    val STANDARD = WorldBuilder { config, listener, params -> StandardWorld.build(config, listener, params) }
  }
}
