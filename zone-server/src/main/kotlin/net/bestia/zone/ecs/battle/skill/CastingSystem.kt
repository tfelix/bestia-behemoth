package net.bestia.zone.ecs.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.skill.SkillExecutionService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Drives the cast-time countdown. Every tick it advances each [Casting] and, on the tick it elapses,
 * drops the component and resolves the skill.
 *
 * Removing the component is also what tells the client the bar is done - the same signal an
 * interrupt produces, since visually both just end the cast. Interruption
 * itself is not handled here; it happens by removing the component elsewhere (see
 * [CastCancelService] for message handlers and
 * [net.bestia.zone.ecs.battle.damage.ReceivedDamageSystem] for damage).
 */
@SpringComponent
@Order(44)
class CastingSystem(
  private val skillExecutionService: SkillExecutionService,
) : System {

  override val writes: ComponentClassSet = setOf(Casting::class)

  override fun update(world: World, deltaTime: Float) {
    // Collected first so the component removals below don't mutate what we're iterating over.
    var completed: MutableList<Pair<EntityId, Casting>>? = null

    world.query(Casting::class).each { id ->
      val casting = get<Casting>()
      casting.remainingSeconds -= deltaTime

      if (casting.remainingSeconds <= 0f) {
        (completed ?: mutableListOf<Pair<EntityId, Casting>>().also { completed = it }).add(id to casting)
      }
    }

    completed?.forEach { (id, casting) ->
      LOG.debug { "Cast of skill ${casting.skillId} by entity $id completed" }

      world.remove(id, Casting::class)

      skillExecutionService.execute(
        world = world,
        casterId = id,
        skillId = casting.skillId,
        skillLevel = casting.skillLevel,
        targetEntityId = casting.targetEntityId,
        targetPosition = casting.targetPosition
      )
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
