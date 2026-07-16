package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Ticks down every active effect's remaining duration and removes expired ones. Ordered after
 * [StatusEffectDamageInterceptSystem] (45) so an effect whose duration reaches zero this tick still gets a
 * chance to trigger before it expires.
 */
@SpringComponent
@Order(46)
class StatusEffectDurationSystem : System {

  override val schedule: Schedule = Schedule.EveryTick
  override val writes: ComponentClassSet = setOf(StatusEffects::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(StatusEffects::class).each {
      // tickDown marks the component dirty itself if any effect expired.
      get<StatusEffects>().tickDown(deltaTime)
    }
  }
}
