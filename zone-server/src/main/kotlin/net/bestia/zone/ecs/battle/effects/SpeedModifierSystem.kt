package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.battle.status.StatType
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Speed
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Recomputes an entity's effective [Speed.speed] from [Speed.baseSpeed] and any active
 * [StatType.SPEED] modifiers. Runs after [StatAggregationSystem] (47). This is the concrete,
 * worked example of the stat-modifier extensibility seam: a future buffable stat gets its own
 * small system shaped exactly like this one.
 */
@SpringComponent
@Order(48)
class SpeedModifierSystem : System {

  override val reads: ComponentClassSet = setOf(StatModifiers::class)
  override val writes: ComponentClassSet = setOf(Speed::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Speed::class, StatModifiers::class).each { _ ->
      val speed = get<Speed>()
      val modifiers = get<StatModifiers>()

      val effective = modifiers.effective(speed.baseSpeed, StatType.SPEED)
      if (speed.speed != effective) {
        speed.speed = effective
      }
    }
  }
}
