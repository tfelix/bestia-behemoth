package net.bestia.zone.ai.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.behavior.BtContext
import net.bestia.zone.ai.behavior.Status
import net.bestia.zone.ecs.Entity
import net.bestia.zone.ecs.IteratingSystem
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.ecs.movement.Position
import org.springframework.stereotype.Component

/**
 * Final stage of the AI pipeline (Behaviour Tree + Movement/Combat). Runs every tick: it ticks the
 * behaviour tree of the plan's current action. Leaves mutate the dirtyable `Path`/combat components,
 * which is how AI decisions become client-visible. On action SUCCESS it advances the plan; when the
 * plan finishes or an action FAILs it clears the plan so the think stage replans next cycle.
 */
@Component
class AiActSystem : IteratingSystem() {

  override val requiredComponents = setOf(Brain::class, Position::class)

  override fun update(deltaTime: Float, entity: Entity, zone: ZoneServer) {
    val brain = entity.getOrThrow(Brain::class)
    val node = brain.currentActionNode ?: return

    if (brain.attackCooldownRemaining > 0f) {
      brain.attackCooldownRemaining = (brain.attackCooldownRemaining - deltaTime).coerceAtLeast(0f)
    }

    val context = BtContext(entity, brain, zone, deltaTime)

    when (node.tick(context)) {
      Status.SUCCESS -> {
        val next = brain.advancePlan()
        if (next == null) {
          // Plan complete; think will select the next goal/plan.
          brain.clearPlan()
        }
      }

      Status.FAILURE -> {
        LOG.trace { "Entity ${entity.id} action failed; clearing plan for replan" }
        brain.clearPlan()
      }

      Status.RUNNING -> {
        // keep executing the current action next tick
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
