package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component as SpringComponent

/** Frees kill loot for everyone once its [LootProtection] has run out. */
@SpringComponent
class LootProtectionSystem : System {

  override val phase = Phase.WORLD
  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val writes: ComponentClassSet = setOf(LootProtection::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(LootProtection::class).each { id ->
      val protection = get<LootProtection>()
      protection.remainingSeconds -= deltaTime
      if (protection.remainingSeconds <= 0f) {
        world.remove(id, LootProtection::class)
      }
    }
  }
}
