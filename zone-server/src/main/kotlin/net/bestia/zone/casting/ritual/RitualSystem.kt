package net.bestia.zone.casting.ritual

import net.bestia.zone.battle.ecs.skill.RitualParticipant
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component as SpringComponent

/**
 * Counts a running ritual down. A helper who dropped out of the channel adds nothing; a leader who dropped out
 * fails the ritual; the end of the 30 seconds charges the leader. See [RitualService].
 */
@SpringComponent
class RitualSystem(
  private val rituals: RitualService,
) : System {
  override val phase = Phase.ACTIONS
  override val reads: ComponentClassSet = setOf(Ritual::class, RitualParticipant::class)
  override val writes: ComponentClassSet = setOf(Ritual::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Ritual::class).each { circleId ->
      val ritual = get<Ritual>()
      val remaining = ritual.remainingSeconds ?: return@each

      // Deferred: completing or failing takes the circle out of the world and changes the participants'
      // components, which is structural while this system iterates.
      if (!world.has(ritual.leaderId, RitualParticipant::class)) {
        world.defer { rituals.fail(world, circleId, ritual) }
        return@each
      }

      ritual.remainingSeconds = remaining - deltaTime
      if (remaining - deltaTime <= 0f) {
        ritual.remainingSeconds = null
        world.defer { rituals.complete(world, circleId, ritual) }
      }
    }
  }
}
