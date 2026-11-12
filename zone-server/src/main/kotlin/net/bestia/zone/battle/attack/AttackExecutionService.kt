package net.bestia.zone.battle.attack

import net.bestia.zone.skill.OwnerSkills
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.movement.ecs.Facing
import net.bestia.zone.battle.damage.Blocked
import net.bestia.zone.battle.damage.weighedTo
import net.bestia.zone.battle.damage.DamageLanding
import net.bestia.zone.battle.damage.HitReach
import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.battle.damage.HitForm
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ActionGuard
import net.bestia.zone.battle.ActionReactions
import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.ammo.AmmoSupply
import net.bestia.zone.battle.ammo.Ammunition
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.battle.GearWear
import net.bestia.zone.battle.Healing
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.damage.DamageGate
import net.bestia.zone.battle.damage.ShieldGuard
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.battle.status.AttackSpeed
import net.bestia.zone.battle.ecs.attack.AttackDelay
import net.bestia.zone.battle.status.EntityAction
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.scripts.Corrosive
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Grounded
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.PropVitality
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import net.bestia.zone.battle.damage.Damage as DamageResult
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.ecs.core.update

/**
 * Resolves a **basic attack** - a sword swing, an arrow, a mob's bite.
 *
 * Separate from [net.bestia.zone.casting.SkillExecutionService] because the two have almost nothing in common beyond the word
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
  private val basicAttacks: BasicAttackResolver,
  private val ammoSupply: AmmoSupply,
  private val actionGuard: ActionGuard = ActionGuard(),
  private val actionReactions: ActionReactions = ActionReactions.NONE,
  private val gearWear: GearWear = GearWear.NONE,
  private val swingProcs: List<SwingProc> = emptyList(),
  private val felling: Felling? = null,
  private val shieldGuard: ShieldGuard? = null,
  private val tripleBlow: TripleBlow? = null,
) {

  /** [READS] plus what spending an arrow reads, which only the item slice can name. */
  val reads: ComponentClassSet = READS + ammoSupply.reads

  val writes: ComponentClassSet = WRITES + ammoSupply.writes

  /** Attacks with whatever [attackerId] holds: a shot from a ranged weapon, else [fallback]. */
  fun attackWithHeldWeapon(
    world: World,
    attackerId: EntityId,
    targetId: EntityId,
    fallback: BattleAttack = BattleAttack.getBasicMeleeAttack(),
  ): AttackOutcome {
    val attack = basicAttacks.resolve(world, attackerId, fallback) ?: return outOfAmmo(world, attackerId)

    return attack(world, attackerId, targetId, attack)
  }

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

    // A client that names a hidden target saw it through no window the server gave it.
    if (world.get(targetId, StatusEffects::class)?.isConcealed() == true) {
      return AttackOutcome.IMPOSSIBLE
    }

    // Not ready rather than refused: a standing attack order resumes once the stun wears off.
    if (actionGuard.refuses(world, attackerId, ActionLock.ATTACK)) {
      return AttackOutcome.NOT_READY
    }

    // Before the context is built, and silently: with a standing attack order this is the answer on almost
    // every tick, so it must cost one lookup and must not reach the log.
    val delay = world.get(attackerId, AttackDelay::class)
    if (delay != null && delay.remainingSeconds > 0f) {
      return AttackOutcome.NOT_READY
    }

    // Before the roll, so a swing that may not land neither shows a miss nor costs the attack delay.
    if (DamageGate.verdict(world, attackerId, targetId) != DamageGate.Verdict.ADMITTED) {
      return AttackOutcome.REFUSED
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

    // Spent only for a shot that will be fired, so stepping out of reach costs no arrow.
    val ammunition = attack.ammunition
    if (ammunition != null) {
      if (!ammoSupply.draw(world, attackerId, ammunition)) {
        return outOfAmmo(world, attackerId)
      }
      ammunition.effect.beforeHit(ctx)
    }

    val result = strategy.execute(ctx)
    val offHandResult = offHandBlow(world, attackerId, targetId, ctx, strategy, result)
    world.get(attackerId, Facing::class)?.turnTowards(ctx.attacker.position, ctx.targetPosition())

    // Armed only once the swing is real, so stepping out of reach neither costs the attacker its delay nor
    // offers a way to sidestep it.
    world.update(attackerId, { AttackDelay() }) {
      it.remainingSeconds = AttackSpeed.delaySeconds(
        attack.baseAttackMotionMs, ctx.attacker.statusValues, ctx.attacker.aspdPercent
      )
    }

    apply(world, attackerId, targetId, result, ammunition, hitKindOf(ctx.usedAttack))
    offHandResult?.let { apply(world, attackerId, targetId, it, ammunition = null, hitKindOf(ctx.usedAttack), isOffHand = true) }
    actionReactions.notifyActed(world, attackerId, EntityAction.ATTACKED)
    corrode(world, attackerId, targetId, attack, result)

    return AttackOutcome.SWUNG
  }

  /**
   * A dual wielder's second weapon follows a melee swing that connected, at Dual Wield's share of a blow. Not on a
   * tree: only the axe in the main hand fells it.
   */
  private fun offHandBlow(
    world: World,
    attackerId: EntityId,
    targetId: EntityId,
    ctx: BattleContext,
    strategy: AttackStrategy,
    swung: DamageResult,
  ): DamageResult? {
    if (swung is Miss || ctx !is EntityBattleContext || ctx.usedAttack.attackType != AttackType.MELEE_PHYSICAL) return null
    if (felling?.isTree(world, targetId) == true) return null
    val combat = world.get(attackerId, CombatStats::class)?.values ?: return null
    val offHand = combat.offHandWeapon?.takeIf { combat.offHandDamagePercent > 0 } ?: return null

    return strategy.execute(ctx.withOffHand(offHand, combat.offHandDamagePercent))
  }

  private fun outOfAmmo(world: World, attackerId: EntityId): AttackOutcome {
    world.get(attackerId, Account::class)?.let {
      outMessageProcessor.sendToPlayer(it.accountId, OperationErrorSMSG(OpError.ATTACK_OUT_OF_AMMO))
    }

    return AttackOutcome.OUT_OF_AMMO
  }

  /** A melee blow that lands on a corrosive hide costs the weapon some durability. */
  private fun corrode(world: World, attackerId: EntityId, targetId: EntityId, attack: BattleAttack, result: DamageResult) {
    if (result is Miss || attack.attackType != AttackType.MELEE_PHYSICAL) return
    if (world.get(targetId, StatusEffects::class)?.hasEffect(StatusEffectId.CORROSIVE.id) != true) return

    gearWear.wearWeapon(world, attackerId, Corrosive.WEAR_PER_HIT)
  }

  /**
   * A blow on a tree: without an axe or a felling tool it does nothing and the attacker is told; with one,
   * Lumberjack brings the tree down sooner. Null means the blow is spent on nothing.
   */
  private fun onTree(world: World, attackerId: EntityId, targetId: EntityId, result: DamageResult): DamageResult? {
    val rules = felling ?: return result
    if (!rules.isTree(world, targetId)) return result
    if (!rules.canFell(world, attackerId)) {
      world.get(attackerId, Account::class)?.let {
        outMessageProcessor.sendToPlayer(it.accountId, OperationErrorSMSG(OpError.FELLING_NEEDS_AXE))
      }
      return null
    }

    return result.weighedTo(rules.felled(world, attackerId, result.amount))
  }

  /**
   * The main hand's landed melee swing as a Triple Blow, or null when it stays one blow. Once per attack: the second
   * weapon only follows the swing. Not on a tree, which only an axe fells.
   */
  private fun tripled(
    world: World,
    attackerId: EntityId,
    targetId: EntityId,
    landed: DamageResult,
    kind: HitKind,
    isOffHand: Boolean,
  ): DamageResult? {
    if (isOffHand || kind.form != HitForm.PHYSICAL || kind.reach != HitReach.MELEE) return null
    if (felling?.isTree(world, targetId) == true) return null
    if (tripleBlow?.rolls(world, attackerId) != true) return null

    return landed.weighedTo(landed.amount * TripleBlow.DAMAGE_PERCENT / 100)
  }

  private fun hitKindOf(attack: BattleAttack): HitKind {
    return when (attack.attackType) {
      AttackType.MAGIC -> HitKind.magic(attack.attackElement)
      AttackType.RANGED_PHYSICAL -> HitKind.physical(HitReach.RANGED, attack.attackElement)
      AttackType.MELEE_PHYSICAL -> HitKind.physical(HitReach.MELEE, attack.attackElement)
    }
  }

  private fun apply(
    world: World,
    attackerId: EntityId,
    targetId: EntityId,
    swung: DamageResult,
    ammunition: Ammunition?,
    kind: HitKind,
    isOffHand: Boolean = false,
  ) {
    if (!world.has(targetId, Position::class)) return

    fun messageOf(result: DamageResult, blows: Int) = DamageEntitySMSG(
      entityId = targetId,
      sourceEntityId = attackerId,
      // 0 is "no catalogue entry", which is what a basic attack is - the client falls back to its
      // default swing rather than looking up an AttackResource that does not exist.
      attackId = 0,
      div = blows,
      damage = result.amount,
      skillLevel = 1,
      type = DamageEntitySMSG.DamageType.of(result),
      projectileItemId = ammunition?.itemId ?: 0
    )

    // Deferred because this is called from inside a system: `World.add` is itself deferred while a system
    // iterates, so staging inline would let two swings landing on the same target in one tick each create
    // their own IncomingDamage component with the second silently replacing the first. Inside a deferred block
    // structural changes apply immediately, so the get-or-create below is sound.
    world.defer {
      // Re-checked inside the deferred block, not outside it: something later in this same tick may have
      // destroyed the target between the swing and the drain, and `World.add` on a dead entity throws out of
      // `applyDeferred` - taking the rest of the tick's deferred queue, including pending destroys, with it.
      if (!world.isAlive(targetId)) {
        return@defer
      }

      val guarded = shieldGuard?.guard(world, targetId, kind, swung) ?: swung
      val result = onTree(world, attackerId, targetId, guarded.weighedTo(DamageLanding.weigh(world, attackerId, targetId, kind, guarded.amount)))
        ?: return@defer
      var dealt = result
      var blows = 1
      when (result) {
        is Miss, is Blocked -> Unit

        is Heal -> Healing.heal(world, targetId, result.amount)

        // ReceivedDamageSystem drains this into Health, and handles death, threat and cast interruption.
        else -> {
          // This branch only: a miss and a heal stay true of a target the gate shields.
          if (DamageGate.verdict(world, attackerId, targetId) != DamageGate.Verdict.ADMITTED) {
            return@defer
          }

          tripled(world, attackerId, targetId, result, kind, isOffHand)?.let {
            dealt = it
            blows = TripleBlow.BLOWS
          }

          val staged = world.get(targetId, IncomingDamage::class) ?: world.add(targetId, IncomingDamage())
          staged.add(dealt.amount, attackerId, kind)
          ammunition?.effect?.onHit(world, attackerId, targetId, dealt)
          swingProcs.forEach { it.onSwingLanded(world, attackerId, targetId, dealt) }
        }
      }

      outMessageProcessor.sendToObserversOf(world, targetId, messageOf(dealt, blows))
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** What [attack] reads on both sides, through the battle context and a prop's first promotion. */
    val READS: ComponentClassSet = setOf(
      Position::class, Dead::class, Level::class, Nature::class, StatusEffects::class, CombatStats::class,
      Health::class, AttackDelay::class,
      WorldObjectIdentity::class, PropPose::class, PropVitality::class,
      // Who is told the quiver is empty.
      Account::class,
      // A tree asks what is held and how well its feller knows the axe, see Felling.
      StaticVisual::class,
    ) + DamageGate.READS + DamageLanding.READS + OwnerSkills.READS

    /** What [attack] writes: the staged damage, the attacker's delay, and a prop promoted on its first hit. */
    val WRITES: ComponentClassSet = setOf(
      IncomingDamage::class, AttackDelay::class, Position::class, Grounded::class, Health::class,
      CombatStats::class, Facing::class,
    )
  }
}
