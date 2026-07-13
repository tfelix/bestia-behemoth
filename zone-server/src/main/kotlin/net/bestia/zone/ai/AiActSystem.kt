package net.bestia.zone.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.behavior.BtContext
import net.bestia.zone.ai.behavior.Status
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Path
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Final stage of the AI pipeline (Behaviour Tree + Movement/Combat). Runs every tick: it ticks the
 * behaviour tree of the plan's current action. Leaves mutate the `Path`/combat components, which is
 * how AI decisions become client-visible. On action SUCCESS it advances the plan; when the plan
 * finishes or an action FAILs it clears the plan so the think stage replans next cycle.
 */
@SpringComponent
@Order(30)
class AiActSystem : System {

  override val reads: ComponentClassSet = setOf(Position::class, Brain::class)
  override val writes: ComponentClassSet = setOf(Path::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Brain::class, Position::class).each { id ->
      val brain = get<Brain>()
      // Position is unused here but must stay in the query: it enforces the
      // "only entities that also have Position" join filter.
      val node = brain.currentActionNode ?: return@each

      if (brain.attackCooldownRemaining > 0f) {
        brain.attackCooldownRemaining = (brain.attackCooldownRemaining - deltaTime).coerceAtLeast(0f)
      }

      val context = BtContext(world, id, brain, deltaTime)

      when (node.tick(context)) {
        Status.SUCCESS -> {
          val next = brain.advancePlan()
          if (next == null) {
            // Plan complete; think will select the next goal/plan.
            brain.clearPlan()
          }
        }

        Status.FAILURE -> {
          LOG.trace { "Entity $id action failed; clearing plan for replan" }
          brain.clearPlan()
        }

        Status.RUNNING -> {
          // keep executing the current action next tick
        }
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
