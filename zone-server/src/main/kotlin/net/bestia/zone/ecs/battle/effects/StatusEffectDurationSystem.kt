package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Ticks down every active effect's remaining duration and removes expired ones. Ordered before
 * [StatusValueRecalcSystem] (47) so an effect that just expired is already gone before status
 * values get rebuilt.
 */
@SpringComponent
@Order(46)
class StatusEffectDurationSystem : System {

  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val writes: ComponentClassSet = setOf(StatusEffects::class, IsStatusValueDirty::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(StatusEffects::class).each { id ->
      // tickDown marks the component dirty itself if any effect expired.
      val expired = get<StatusEffects>().tickDown(deltaTime)
      if (expired) {
        world.add(id, IsStatusValueDirty)
      }
    }
  }
}
