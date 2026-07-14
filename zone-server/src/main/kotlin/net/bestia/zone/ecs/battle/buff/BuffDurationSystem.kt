package net.bestia.zone.ecs.battle.buff

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Ticks down every active buff's remaining duration and removes expired ones. Ordered after
 * [BuffDamageInterceptSystem] (45) so a buff whose duration reaches zero this tick still gets a
 * chance to trigger before it expires.
 */
@SpringComponent
@Order(46)
class BuffDurationSystem : System {

  override val schedule: Schedule = Schedule.EveryTick
  override val writes: ComponentClassSet = setOf(Buffs::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Buffs::class).each {
      // tickDown marks the component dirty itself if any buff expired.
      get<Buffs>().tickDown(deltaTime)
    }
  }
}
