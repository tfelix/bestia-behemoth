package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import net.bestia.zone.battle.damage.Damage as DamageResult
import net.bestia.zone.ecs.battle.damage.Damage as DamageComponent

/**
 * Resolves a **basic attack** - a sword swing, an arrow, a mob's bite.
 *
 * Separate from [SkillExecutionService] because the two have almost nothing in common beyond the word
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
   * Swings [attack] at [targetId]. Callable from anywhere the world lock is already held: the tick thread
   * inside a system (the `BasicAttack` behaviour-tree leaf), or a `WorldView.modify` scope on the message
   * thread (`AttackEntityHandler`, when a player clicks something). A player's swing and a mob's bite are
   * the same swing, which is the point.
   */
  fun attack(world: World, attackerId: EntityId, targetId: EntityId, attack: BattleAttack) {
    val ctx = battleContextFactory.create(world, attackerId, attack, targetId, targetPosition = null)
    if (ctx == null) {
      LOG.debug { "Basic attack by $attackerId fizzled: attacker or target no longer resolvable" }
      return
    }

    val strategy = attackStrategyFactory.getAttackStrategy(ctx)
    if (!strategy.isAttackPossible(ctx)) {
      LOG.debug { "Basic attack by $attackerId fizzled: out of range or no line of sight" }
      return
    }

    apply(world, attackerId, targetId, strategy.execute(ctx))
  }

  private fun apply(world: World, attackerId: EntityId, targetId: EntityId, result: DamageResult) {
    val position = world.get(targetId, Position::class)?.toVec3L() ?: return

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
          val staged = world.get(targetId, DamageComponent::class) ?: world.add(targetId, DamageComponent())
          staged.add(result.amount, attackerId)
        }
      }

      outMessageProcessor.sendToAllPlayersInRange(position, msg)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
