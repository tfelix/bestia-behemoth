package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.battle.ecs.status.StaminaRegenSystem
import net.bestia.zone.battle.ecs.status.WorkStrainSystem
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Animation
import net.bestia.zone.entity.ecs.Animation.AnimationKind
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * Keeps `RESTING` on every master and owned bestia that rests now, and only on those, because rest is the only
 * time stamina comes back by itself (see [StaminaRegenSystem]).
 *
 * A body a player drives rests while it sits, lies down or sleeps: the `/sit` and `/lie` emotes, which walking
 * ends. A bestia its own AI drives rests while it has nothing to do: asleep, or idling about its home, which is
 * also where a rule or an empty larder sends it.
 */
@SpringComponent
class RestingSystem(
  private val statusEffects: StatusEffectService,
) : System {
  override val phase = Phase.RECOVERY
  override val after = setOf(WorkStrainSystem::class)
  override val before = setOf(StaminaRegenSystem::class)
  override val schedule: Schedule = Schedule.EverySeconds(1f)

  override val reads: ComponentClassSet = setOf(
    Stamina::class, Animation::class, AiAgent::class, Dead::class, Master::class, OwnedBestia::class
  ) + PlayerHold.READS

  override val writes: ComponentClassSet = setOf(StatusEffects::class, IsStatusValueDirty::class)

  override fun update(world: World, deltaTime: Float) {
    val sitDown = ArrayList<EntityId>()
    val standUp = ArrayList<EntityId>()

    world.query(Stamina::class).each { id ->
      if (!world.has(id, Master::class) && !world.has(id, OwnedBestia::class)) return@each

      val resting = !world.has(id, Dead::class) && isResting(world, id)
      val marked = world.get(id, StatusEffects::class)?.hasEffect(StatusEffectId.RESTING.id) == true
      when {
        resting && !marked -> sitDown += id
        !resting && marked -> standUp += id
      }
    }

    // Applied after the sweep: a first status effect adds a component to the entity the query is still visiting.
    sitDown.forEach { statusEffects.applyEffect(world, it, StatusEffectId.RESTING, level = 1) }
    standUp.forEach { statusEffects.removeEffect(world, it, StatusEffectId.RESTING.id, RemovalReason.CANCELLED) }
  }

  private fun isResting(world: World, id: EntityId): Boolean {
    if (world.get(id, Animation::class)?.currentAnimation in RESTING_POSES) return true

    val agent = world.get(id, AiAgent::class) ?: return false
    if (PlayerHold.isHeld(world, id)) return false

    return agent.currentGoal?.name.let { it == null || it in IDLE_GOALS } &&
      agent.memory.get(BestiaDomain.ENEMY_IN_SIGHT) != true
  }

  private companion object {
    val RESTING_POSES = setOf(AnimationKind.SIT, AnimationKind.LIE, AnimationKind.SLEEP)

    /** What a bestia does when it is not working, fighting or on its way somewhere. */
    val IDLE_GOALS = setOf(
      BestiaDomain.Goals.WANDER.name,
      BestiaDomain.Goals.SLEEP.name,
      BestiaDomain.Goals.EAT_VEGETATION.name,
    )
  }
}
