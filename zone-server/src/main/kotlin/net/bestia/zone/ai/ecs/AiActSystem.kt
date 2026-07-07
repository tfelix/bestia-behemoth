package net.bestia.zone.ai.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.behavior.BtContext
import net.bestia.zone.ai.behavior.Status
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs2.Component
import net.bestia.zone.ecs2.Ecs2System
import net.bestia.zone.ecs2.World
import org.springframework.core.annotation.Order
import kotlin.reflect.KClass
import org.springframework.stereotype.Component as SpringComponent

/**
 * Final stage of the AI pipeline (Behaviour Tree + Movement/Combat). Runs every tick: it ticks the
 * behaviour tree of the plan's current action. Leaves mutate the `Path`/combat components, which is
 * how AI decisions become client-visible. On action SUCCESS it advances the plan; when the plan
 * finishes or an action FAILs it clears the plan so the think stage replans next cycle.
 */
@SpringComponent
@Order(30)
class AiActSystem : Ecs2System {

  override val reads: Set<KClass<out Component>> = setOf(Position::class, Brain::class)
  override val writes: Set<KClass<out Component>> = setOf(net.bestia.zone.ecs.movement.Path::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Brain::class, Position::class).each { id, brain, _ ->
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
