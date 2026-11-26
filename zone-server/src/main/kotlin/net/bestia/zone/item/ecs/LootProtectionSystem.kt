package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component as SpringComponent

/** Runs down every [LootProtection], and frees the drop for everyone once its last claim has run out. */
@SpringComponent
class LootProtectionSystem : System {

  override val phase = Phase.WORLD
  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val writes: ComponentClassSet = setOf(LootProtection::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(LootProtection::class).each { id ->
      if (!get<LootProtection>().countDown(deltaTime)) {
        world.remove(id, LootProtection::class)
      }
    }
  }
}
