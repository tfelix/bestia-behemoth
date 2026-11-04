package net.bestia.zone.world

import net.bestia.worldgen.core.StageListener
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.pipeline.WorldParams
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** Gives a Spring test context the same world the unit tests read, instead of generating one more. */
@Component
@Profile("test")
class SharedWorldBuilder : WorldBuilder {

  override fun build(config: WorldConfig, listener: StageListener, params: WorldParams): GeneratedWorld {
    return TestWorlds.get(config, params)
  }
}
