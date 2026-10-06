package net.bestia.zone.battle.attack

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.status.AttackSpeed
import net.bestia.zone.battle.ecs.attack.AttackDelay
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatBonus
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Invulnerable
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.ecs.status.StatusValues
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.item.Equipment
import net.bestia.zone.movement.ecs.Grounded
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.PropVitality
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import net.bestia.zone.battle.damage.Damage as DamageResult
import net.bestia.zone.battle.ecs.damage.Damage as DamageComponent
import net.bestia.zone.ecs.core.update

/**
 * Resolves a **basic attack** - a sword swing, an arrow, a mob's bite.
 *
 * Separate from [net.bestia.zone.battle.skill.SkillExecutionService] because the two have almost nothing in common beyond the word
 * "attack". A basic attack has no catalogue row, no script, no mana and no cast bar; it is the weapon and
 * the stats and nothing else. Forcing it through the skill pipeline meant every swing paid for a
 * repository lookup, a script registry lookup and a scripting context, and it is why mobs currently cast a
 * skill id that is not in `skills.yml` at all.
 *
 * Unlike a skill this runs inline on the caller's thread. It needs no budget and no async hop because it
 * does no world manipulation: it reads a snapshot, computes a number, stages it on the target.
 */
@Service
class AttackExecutionService(
  private val battleContextFactory: BattleContextFactory,
  private val attackStrategyFactory: AttackStrategyFactory,
  private val outMessageProcessor: OutMessageProcessor,
) {

  /**
   * Swings [attack] at [targetId]. Callable from anywhere that has the world to itself: the tick thread
   * inside a system (the `BasicAttack` behaviour-tree leaf), or a `WorldView.modify` scope in a message
   * handler (`AttackEntityHandler`, when a player clicks something). A player's swing and a mob's bite are
   * the same swing, which is the point.
   *
   * Being the one place both reach, this is also where [AttackDelay] is enforced and armed - there is no
   * second cadence anywhere, and no caller can swing faster by asking more often.
   */
  fun attack(world: World, attackerId: EntityId, targetId: EntityId, attack: BattleAttack): AttackOutcome {
    // A player-owned body stays in the world after it dies, so "still alive" is no longer the same
    // question as "still a valid combatant". Neither end of a swing may be a corpse.
    if (world.has(attackerId, Dead::class) || world.has(targetId, Dead::class)) {
      LOG.debug { "Basic attack by $attackerId at $targetId fizzled: one of them is dead" }
      return AttackOutcome.IMPOSSIBLE
    }

    // Before the context is built, and silently: with a standing attack order this is the answer on almost
    // every tick, so it must cost one lookup and must not reach the log.
    val delay = world.get(attackerId, AttackDelay::class)
    if (delay != null && delay.remainingSeconds > 0f) {
      return AttackOutcome.NOT_READY
    }

    val ctx = battleContextFactory.create(world, attackerId, attack, targetId, targetPosition = null)
    if (ctx == null) {
      LOG.debug { "Basic attack by $attackerId fizzled: attacker or target no longer resolvable" }
      return AttackOutcome.IMPOSSIBLE
    }

    val strategy = attackStrategyFactory.getAttackStrategy(ctx)
    if (!strategy.isAttackPossible(ctx)) {
      // Trace, not debug: a standing order against a target that has stepped away asks again every tick, and
      // nothing arms the attack delay to slow it down - a swing that did not happen must not cost one.
      LOG.trace { "Basic attack by $attackerId fizzled: out of range or no line of sight" }
      return AttackOutcome.OUT_OF_RANGE
    }

    val result = strategy.execute(ctx)

    // Armed only once the swing is real, so stepping out of reach neither costs the attacker its delay nor
    // offers a way to sidestep it.
    world.update(attackerId, { AttackDelay() }) {
      it.remainingSeconds = AttackSpeed.delaySeconds(attack.baseAttackMotionMs, ctx.attacker.statusValues)
    }

    apply(world, attackerId, targetId, result)

    return AttackOutcome.SWUNG
  }

  private fun apply(world: World, attackerId: EntityId, targetId: EntityId, result: DamageResult) {
    if (!world.has(targetId, Position::class)) return

    val msg = DamageEntitySMSG(
      entityId = targetId,
      sourceEntityId = attackerId,
      // 0 is "no catalogue entry", which is what a basic attack is - the client falls back to its
      // default swing rather than looking up an AttackResource that does not exist.
      attackId = 0,
      div = 1,
      damage = result.amount,
      skillLevel = 1,
      type = DamageEntitySMSG.DamageType.of(result)
    )

    // Deferred because this is called from inside a system: `World.add` is itself deferred while a system
    // iterates, so staging inline would let two swings landing on the same target in one tick each create
    // their own Damage component with the second silently replacing the first. Inside a deferred block
    // structural changes apply immediately, so the get-or-create below is sound.
    world.defer {
      // Re-checked inside the deferred block, not outside it: something later in this same tick may have
      // destroyed the target between the swing and the drain, and `World.add` on a dead entity throws out of
      // `applyDeferred` - taking the rest of the tick's deferred queue, including pending destroys, with it.
      if (!world.isAlive(targetId)) {
        return@defer
      }

      when (result) {
        is Miss -> Unit

        // CurMax.current clamps to [0, max] itself.
        is Heal -> world.get(targetId, Health::class)?.let { it.current += result.amount }

        // ReceivedDamageSystem drains this into Health, and handles death, threat and cast interruption.
        else -> {
          // This branch only - see Invulnerable: a miss and a heal stay true of a target that cannot be hurt.
          if (world.has(targetId, Invulnerable::class)) {
            return@defer
          }

          val staged = world.get(targetId, DamageComponent::class) ?: world.add(targetId, DamageComponent())
          staged.add(result.amount, attackerId)
        }
      }

      outMessageProcessor.sendToObserversOf(world, targetId, msg)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** What [attack] reads on both sides, through the battle context and a prop's first promotion. */
    val READS: ComponentClassSet = setOf(
      Position::class, Dead::class, Level::class, Nature::class, StatusEffects::class, StatusValues::class,
      CombatBonus::class, Equipment::class, Invulnerable::class, Health::class, AttackDelay::class,
      WorldObjectIdentity::class, PropPose::class, PropVitality::class,
    )

    /** What [attack] writes: the staged damage, the attacker's delay, and a prop promoted on its first hit. */
    val WRITES: ComponentClassSet = setOf(
      DamageComponent::class, AttackDelay::class, Position::class, Grounded::class, Health::class,
      StatusValues::class,
    )
  }
}
