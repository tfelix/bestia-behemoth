package net.bestia.zone.ai.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.goal.UtilityScorer
import net.bestia.zone.ai.goal.consideration.DecisionContext
import net.bestia.zone.ai.planner.GoapActionRegistry
import net.bestia.zone.ai.planner.Planner
import net.bestia.zone.ai.planner.WorldStateBuilder
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.ecs.Entity
import net.bestia.zone.ecs.PeriodicSystem
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.movement.Position
import org.springframework.stereotype.Component

/**
 * Second stage of the AI pipeline (Memory + Goal + Planner). Runs periodically (~0.5s): it distils
 * the brain snapshot into a [DecisionContext], lets the [UtilityScorer] pick the winning goal, and
 * — when the goal changes or the current plan is spent — asks the GOAP [Planner] for a fresh plan.
 * It only produces plans; the act system consumes them.
 */
@Component
class AiThinkSystem(
  private val profileRegistry: AiProfileRegistry,
  private val scorer: UtilityScorer,
  private val worldStateBuilder: WorldStateBuilder,
  private val planner: Planner,
  private val actionRegistry: GoapActionRegistry
) : PeriodicSystem(
  delay = 0.5f,
  setOf(Brain::class, Position::class, Health::class)
) {

  override fun update(deltaTime: Float, entity: Entity, zone: ZoneServer) {
    val brain = entity.getOrThrow(Brain::class)
    val profile = profileRegistry.get(brain.profileId) ?: return
    val selfPos = entity.getOrThrow(Position::class).toVec3L()
    val health = entity.getOrThrow(Health::class)

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
      return
    }

    val goal = scored.goal
    val needsReplan = brain.currentGoal?.name != goal.name || !brain.hasActivePlan()
    if (!needsReplan) {
      return
    }

    val worldState = worldStateBuilder.build(brain, context, selfPos)
    val actions = actionRegistry.resolve(profile.actionIds)
    val plan = planner.plan(worldState, goal.desiredState, actions)

    if (plan == null || plan.isEmpty) {
      // Goal unreachable or already satisfied: nothing to execute this cycle.
      brain.clearPlan()
      brain.currentGoal = goal
      return
    }

    brain.currentGoal = goal
    brain.currentPlan = plan
    brain.planCursor = 0
    brain.currentActionNode = plan.actions.first().behaviorTree()

    LOG.trace { "Entity ${entity.id} adopts goal '${goal.name}' with plan ${plan.actions.map { it.id }}" }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
