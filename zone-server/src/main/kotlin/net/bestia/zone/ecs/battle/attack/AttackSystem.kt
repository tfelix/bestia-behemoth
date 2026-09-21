package net.bestia.zone.ecs.battle.attack

import net.bestia.zone.battle.skill.AttackExecutionService
import net.bestia.zone.battle.skill.AttackOutcome
import net.bestia.zone.battle.skill.BattleAttack
import net.bestia.zone.ecs.battle.damage.Damage
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.battle.effects.StatusEffects
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Invulnerable
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Grounded
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.prop.PropPose
import net.bestia.zone.ecs.prop.PropVitality
import net.bestia.zone.ecs.prop.WorldObjectIdentity
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Counts down every [AttackDelay] and swings for everything holding an [AttackTarget].
 *
 * Both passes live here because a mob has an attack delay without ever holding a standing order, so the
 * countdown cannot hang off the target query.
 *
 * `@Order(49)`: after `StatusValueRecalcSystem` (@47), which rebuilds the `StatusValues` the delay is derived
 * from, after `RespawnSystem` (@44) so a body revived this tick does not swing on it, and before `DeathSystem`
 * (@70) - the deferred queue is FIFO and `World.addNow` rejects a dead entity, so [Damage] enqueued after a
 * destroy would throw out of `applyDeferred` and take the rest of the queue with it.
 */
@SpringComponent
@Order(49)
class AttackSystem(
  private val attackExecutionService: AttackExecutionService,
) : System {

  override val schedule: Schedule = Schedule.EveryTick

  /**
   * The three prop components are `PropPromotionService`'s, read off a target being hit for the first time.
   * `PropSupportSystem`, `WorldObjectResidencySystem` and `ConstructionSystem` all write them.
   */
  override val reads: ComponentClassSet = setOf(
    Dead::class, Level::class, StatusEffects::class, Invulnerable::class,
    WorldObjectIdentity::class, PropPose::class, PropVitality::class
  )

  /**
   * Includes what the swing writes to the *target* and what `PropPromotionService` adds to a prop being hit
   * for the first time, not just what this touches on the attacker. `SystemScheduler.conflicts()` looks at
   * nothing but these sets, and the store being mutated does not care whose entity it belongs to.
   */
  override val writes: ComponentClassSet = setOf(
    AttackTarget::class, AttackDelay::class, Damage::class,
    Position::class, Grounded::class, Health::class, StatusValues::class
  )

  // TODO Take the weapon and its element off the attacker once an equipment system exists.
  private val basicAttack = BattleAttack.getBasicMeleeAttack()

  override fun update(world: World, deltaTime: Float) {
    world.query(AttackDelay::class).each { _ ->
      val delay = get<AttackDelay>()
      delay.remainingSeconds = (delay.remainingSeconds - deltaTime).coerceAtLeast(0f)
    }

    world.query(AttackTarget::class).each { id ->
      val targetId = get<AttackTarget>().targetEntityId

      if (shouldGiveUp(world, id, targetId)) {
        // Deferred like any structural change mid-tick, so the order survives to the end of this tick and is
        // gone by the next - which is why it is safe to drop it from inside the query.
        world.remove(id, AttackTarget::class)
      }
    }
  }

  /**
   * A player-owned body stays alive in the ECS sense after it dies, so `isAlive` alone would keep an order
   * pointed at a corpse forever.
   */
  private fun shouldGiveUp(world: World, attackerId: Long, targetId: Long): Boolean {
    if (!world.isAlive(targetId) || world.has(targetId, Dead::class) || world.has(attackerId, Dead::class)) {
      return true
    }

    // Out of range and still on the attack delay both keep the order: the player asked for this target and
    // only they, or a death, may take it away.
    return attackExecutionService.attack(world, attackerId, targetId, basicAttack) == AttackOutcome.IMPOSSIBLE
  }
}
