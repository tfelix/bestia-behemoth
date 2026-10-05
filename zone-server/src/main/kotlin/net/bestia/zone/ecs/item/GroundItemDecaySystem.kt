package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component as SpringComponent

/**
 * Removes plain items that lay on the ground too long. Every one is an entity that is streamed, persisted and
 * loaded again on every start, so without this a player dropping items one by one could fill the world.
 *
 * Destroyed like a picked-up stack: the destroy tells the clients, and the persisted row is pruned with it.
 */
@SpringComponent
class GroundItemDecaySystem : System {

  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val reads: ComponentClassSet = emptySet()
  override val writes: ComponentClassSet = setOf(GroundItemDecay::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(GroundItemDecay::class).each { id ->
      val decay = get<GroundItemDecay>()
      decay.remainingSeconds -= deltaTime

      if (decay.remainingSeconds <= 0f) {
        world.destroy(id)
      }
    }
  }
}
