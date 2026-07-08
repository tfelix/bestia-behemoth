package net.bestia.zone.ai.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.goal.UtilityScorer
import net.bestia.zone.ai.goal.consideration.DecisionContext
import net.bestia.zone.ai.planner.GoapActionRegistry
import net.bestia.zone.ai.planner.Planner
import net.bestia.zone.ai.planner.WorldStateBuilder
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.Ecs2System
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import kotlin.reflect.KClass
import org.springframework.stereotype.Component as SpringComponent

/**
 * Second stage of the AI pipeline (Memory + Goal + Planner). Runs periodically (~0.5s): it distils
 * the brain snapshot into a [DecisionContext], lets the [UtilityScorer] pick the winning goal, and
 * — when the goal changes or the current plan is spent — asks the GOAP [Planner] for a fresh plan.
 * It only produces plans; the act system consumes them.
 */
@SpringComponent
@Order(20)
class AiThinkSystem(
  private val profileRegistry: AiProfileRegistry,
  private val scorer: UtilityScorer,
  private val worldStateBuilder: WorldStateBuilder,
  private val planner: Planner,
  private val actionRegistry: GoapActionRegistry
) : Ecs2System {

  override val schedule: Schedule = Schedule.EverySeconds(0.5f)
  override val reads: Set<KClass<out Component>> = setOf(Position::class, Health::class, Brain::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Brain::class, Position::class, Health::class).each { id ->
      val brain = get<Brain>()
      val position = get<Position>()
      val health = get<Health>()

      val profile = profileRegistry.get(brain.profileId) ?: return@each
      val selfPos = position.toVec3L()

      // Events are drained so the queue stays bounded; reactive handling can hook in here later.
      brain.drainEvents()

      val snapshot = brain.latestPercept
      val hostiles = snapshot?.hostiles.orEmpty()
      val context = DecisionContext(
        profile = profile,
        ownHealthPct = if (health.max > 0) health.current.toDouble() / health.max else 0.0,
        enemyInSight = hostiles.isNotEmpty(),
        nearestEnemyDistance = hostiles.minOfOrNull { selfPos.distance(it.position) }
      )

      val scored = scorer.selectGoal(context)
      if (scored == null) {
        brain.clearPlan()
        return@each
      }

      val goal = scored.goal
      val needsReplan = brain.currentGoal?.name != goal.name || !brain.hasActivePlan()
      if (!needsReplan) {
        return@each
      }

      val worldState = worldStateBuilder.build(brain, context, selfPos)
      val actions = actionRegistry.resolve(profile.actionIds)
      val plan = planner.plan(worldState, goal.desiredState, actions)

      if (plan == null || plan.isEmpty) {
        // Goal unreachable or already satisfied: nothing to execute this cycle.
        brain.clearPlan()
        brain.currentGoal = goal
        return@each
      }

      brain.currentGoal = goal
      brain.currentPlan = plan
      brain.planCursor = 0
      brain.currentActionNode = plan.actions.first().behaviorTree()

      LOG.trace { "Entity $id adopts goal '${goal.name}' with plan ${plan.actions.map { it.id }}" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
