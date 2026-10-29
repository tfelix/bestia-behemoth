package net.bestia.zone.entity.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component as SpringComponent

/** Takes an [EntityFx] off once it has played, so the next effect is a change observers are told about. */
@SpringComponent
class EntityFxSystem : System {
  override val phase = Phase.STATUS

  override val reads: ComponentClassSet = emptySet()
  override val writes: ComponentClassSet = setOf(EntityFx::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(EntityFx::class).each { id ->
      val fx = get<EntityFx>()
      fx.secondsLeft -= deltaTime
      if (fx.secondsLeft <= 0f) {
        world.remove(id, EntityFx::class)
      }
    }
  }
}
