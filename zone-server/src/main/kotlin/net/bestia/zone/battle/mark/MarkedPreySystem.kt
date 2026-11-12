package net.bestia.zone.battle.mark

import net.bestia.zone.battle.ecs.effects.StatusEffectDurationSystem
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import org.springframework.stereotype.Component as SpringComponent

/** Follows every hunter's prey once a second, for the hunter's map. A prey that is gone ends the mark here. */
@SpringComponent
class MarkedPreySystem : System {
  override val phase = Phase.STATUS
  override val after = setOf(StatusEffectDurationSystem::class)
  override val schedule: Schedule = Schedule.EverySeconds(1f)

  override val reads: ComponentClassSet = setOf(Position::class, StatusEffects::class)
  override val writes: ComponentClassSet = setOf(MarkedPrey::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(MarkedPrey::class).each { hunterId ->
      val marked = get<MarkedPrey>()
      val preyId = marked.preyId ?: return@each

      val at = if (world.isAlive(preyId)) world.get(preyId, Position::class)?.toVec3L() else null
      if (at == null || HuntersMarks.levelOn(world, hunterId, preyId) == 0) {
        marked.preyId = null
        return@each
      }
      marked.follow(at)
    }
  }
}
