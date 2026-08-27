package net.bestia.zone.ai.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.Posture
import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ai.core.planner.EffectWriteBack
import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.ecs.battle.damage.Damage
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.entity.Animation
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Mana
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.navigation.MacroRoute
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Final stage of the AI pipeline: tick the behaviour tree of the plan step currently being carried out.
 * Leaves mutate `Path`, cast skills and so on, which is how an AI decision becomes visible to players.
 *
 * ### Effects are applied on observed success, not at plan time
 *
 * When a step's tree reports SUCCESS, *that* is when the action's effects are written back to memory —
 * one action at a time, cascaded by each key's scope. This is the correction of a real defect in the old
 * plan executor, which folded a whole plan's effects into the blackboard the moment the plan was made.
 * That made an agent believe everything it had merely decided to do: simulating `walkTo(spot)` wrote the
 * spot straight into its idea of its own position, so it believed it had teleported. Now a belief is only
 * recorded once the world has actually been made to match it, and observation keys are never written back
 * at all (see `StateKey.observed`).
 *
 * FAILURE clears the plan so the think stage replans on its next turn; RUNNING simply continues next tick.
 *
 * ### Posture is derived, never latched
 *
 * The same pass keeps the `Animation` component in step with what the creature is *currently* doing — see
 * [updatePosture]. Doing it here rather than inside the leaves is what makes it correct: a plan step can end
 * without its behaviour tree ever being ticked again (a sleeping mob that gets bitten has its plan replaced
 * outright), so a leaf that switched an animation on when it started would have nobody to switch it off.
 */
@SpringComponent
@Order(30)
class AiActSystem(
  private val sharedMemory: SharedMemoryService,
  private val zoneConfig: ZoneConfig,
) : System {

  override val reads: ComponentClassSet = setOf(Position::class, PlayerControlled::class, Dead::class)

  /**
   * Everything the behaviour trees can touch, directly or through the services their leaves hold.
   *
   * `Path`/`MacroRoute` come from locomotion; `Damage`/`Health`/`Mana` from casting a skill, which reaches
   * further than this system's own code does. Naming them is what keeps this out of the same scheduler
   * wave as the movement and combat systems that consume them — the previous declaration listed only
   * `Path` and put the AI in a wave with everything it feeds.
   *
   * Skill execution can reach further still (status effects, outgoing messages). Those are not component
   * writes this system can meaningfully claim, and skills are cast on the tick thread under the world lock
   * by the same route a player's are, so the boundary is drawn at the components above.
   */
  override val writes: ComponentClassSet = setOf(
    AiAgent::class,
    Path::class,
    MacroRoute::class,
    Damage::class,
    Health::class,
    Mana::class,
    Animation::class,
  )

  override fun update(world: World, deltaTime: Float) {
    val worldBoard = sharedMemory.worldBoard()

    world.query(AiAgent::class, Position::class).each { id ->
      val agent = get<AiAgent>()

      // Same belt-and-braces pairing with the think stage as the PlayerControlled check below, for the
      // same reason: a step adopted on the tick before the creature died must not still be carried out
      // by its corpse.
      if (world.has(id, Dead::class)) return@each

      // Belt and braces alongside the same check in the think stage: think drops the plan of a controlled
      // entity, but a plan adopted on the tick before control was taken must not get one more step executed
      // underneath the player's hands.
      if (world.has(id, PlayerControlled::class)) return@each

      val action = agent.currentAction()
      // Deliberately before the two early returns: an agent that has just run out of plan is standing about
      // doing nothing, and that is exactly when its posture has to stop saying otherwise.
      updatePosture(world, id, action)

      val node = agent.currentActionNode ?: return@each
      if (action == null) return@each

      val context = BtContext(
        world = world,
        entityId = id,
        memory = agent.memory,
        state = agent.planState,
        deltaTime = deltaTime,
        currentTick = world.tickCount,
        tickRate = zoneConfig.tickRate,
      )

      when (node.tick(context)) {
        Status.SUCCESS -> {
          // The step really happened, so now — and only now — its effects become beliefs.
          EffectWriteBack.apply(
            before = agent.planState,
            after = action.applyTo(agent.planState),
            individual = agent.memory,
            team = agent.teamMemory,
            world = worldBoard,
          )

          if (agent.advancePlan() == null) {
            LOG.trace { "Entity $id finished its plan" }
            agent.clearPlan()
          }
        }

        Status.FAILURE -> {
          LOG.trace { "Entity $id action '${action.name}' failed; clearing plan for replan" }
          agent.clearPlan()
        }

        Status.RUNNING -> {
          // keep executing the current action next tick
        }
      }
    }
  }

  /**
   * Points the entity's `Animation` at whatever it is doing right now.
   *
   * Only two of the three kinds are decided here. Lying down is knowledge only the plan has — nothing about
   * a sleeping creature's components distinguishes it from one standing still — so it comes from the current
   * action's [Posture]. Walking, on the other hand, is already written on the entity as a `Path`, and reading
   * it there rather than having every movement action declare itself keeps the two from disagreeing.
   *
   * A no-op for anything without the component: mobs get one from the spawner, and an entity that has none
   * simply has no animation to drive.
   */
  private fun updatePosture(world: World, entityId: Long, action: Action?) {
    val animation = world.get(entityId, Animation::class) ?: return

    animation.currentAnimation = when {
      action?.posture == Posture.SLEEPING -> Animation.AnimationKind.SLEEP
      world.has(entityId, Path::class) -> Animation.AnimationKind.WALK
      else -> Animation.AnimationKind.IDLE
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
