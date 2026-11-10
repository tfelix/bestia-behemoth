package net.bestia.zone.battle

import net.bestia.zone.battle.potency.Potency
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.damage.DamageGate
import net.bestia.zone.battle.ecs.damage.DamageReactions
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.EntityAction
import net.bestia.zone.battle.status.StatusEffectDefinition
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectPolarity
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusResistance
import net.bestia.zone.battle.status.scripts.Lifesteal
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import net.bestia.zone.ecs.core.update
import java.util.Random

/**
 * Entry point for applying a status effect to a target entity - what skill scripts and attack
 * handlers call. Resolves the [net.bestia.zone.battle.status.StatusEffectDefinition] and its
 * [net.bestia.zone.battle.status.StatusEffectScript], delegates stacking rules to
 * [StatusEffects.applyEffect], and marks the target for a status value recalc.
 */
@Service
class StatusEffectService(
  private val statusEffectDefinitionRegistry: StatusEffectDefinitionRegistry,
  private val statusEffectScriptRegistry: StatusEffectScriptRegistry,
  private val random: Random = ThreadLocalRandomSource,
) : DamageReactions, ActionReactions {

  /**
   * Returns false when the damage gate turns a harmful effect away or the target's species is immune.
   * [durationSeconds] replaces the script's own duration, for a skill that says how long its effect lasts.
   * [amount] goes to [ActiveStatusEffect.amount], so an endowment can name its element.
   */
  fun applyEffect(
    world: World,
    targetId: EntityId,
    definitionId: Long,
    level: Int,
    sourceEntityId: EntityId? = null,
    durationSeconds: Double? = null,
    amount: Int = 0,
    potency: Int = 0,
  ): Boolean {
    val definition = statusEffectDefinitionRegistry.getOrThrow(definitionId)
    if (isTurnedAway(world, definition, sourceEntityId, targetId)) {
      LOG.debug { "${definition.identifier} from $sourceEntityId turned away from $targetId" }
      return false
    }
    if (isImmune(world, definition, targetId)) {
      return false
    }

    val script = statusEffectScriptRegistry.getOrThrow(definition.script)

    var change = StatusEffects.Change.UNCHANGED
    var landed: ActiveStatusEffect? = null
    world.update(targetId, default = { StatusEffects() }) { effects ->
      change = effects.applyEffect(
        definitionId = definition.id,
        stackBehavior = script.stackBehavior,
        level = level,
        sourceEntityId = sourceEntityId,
        durationSeconds = durationSeconds ?: script.durationSeconds(level),
        isSyncedToClient = definition.isSyncedToClient,
        shield = script.shield,
        polarity = definition.polarity,
        showsCountdown = script.showsCountdown,
        locks = script.actionLocks,
        potency = script.fixedPotency ?: potency
      )
      landed = effects.activeEffects.lastOrNull { it.definitionId == definition.id }
    }
    if (change == StatusEffects.Change.WEAKER) {
      return false
    }
    // On a refresh too: endowing a weapon anew with another element replaces the old one.
    if (amount != 0) {
      landed?.amount = amount
    }

    if (change == StatusEffects.Change.CHANGED) {
      world.add(targetId, IsStatusValueDirty)
      landed?.let { script.onApply(hookContext(world, targetId, it)) }
    }

    return true
  }

  /**
   * Rolls [chancePercent] against the target's resistance before applying, for a skill whose effect only
   * sometimes lands. The resistance also shortens the effect. Returns whether it landed.
   */
  fun tryInflict(
    world: World,
    targetId: EntityId,
    effect: StatusEffectId,
    level: Int,
    sourceEntityId: EntityId?,
    chancePercent: Int,
    durationSeconds: Double? = null,
    amount: Int = 0,
    potency: Int? = null,
  ): Boolean {
    val definition = statusEffectDefinitionRegistry.getOrThrow(effect.id)
    val resistance = resistanceOf(world, definition, targetId, sourceEntityId, potency)

    if (random.nextDouble() * 100.0 >= StatusResistance.chancePercent(chancePercent.toDouble(), resistance)) {
      return false
    }

    val baseSeconds = durationSeconds
      ?: statusEffectScriptRegistry.getOrThrow(definition.script).durationSeconds(level)

    return applyEffect(
      world, targetId, effect.id, level, sourceEntityId, StatusResistance.durationSeconds(baseSeconds, resistance), amount,
      potency ?: 0
    )
  }

  /** Whether [definitionId] on [targetId] came from a stronger cast than [potency], which then cannot refresh it. */
  fun isHeldStronger(world: World, targetId: EntityId, definitionId: Long, potency: Int): Boolean {
    val existing = world.get(targetId, StatusEffects::class)?.activeEffects?.firstOrNull { it.definitionId == definitionId }

    return existing != null && existing.potency > potency
  }

  /** Drops [definitionId] from [targetId] and tells its script why. Returns whether it was there. */
  fun removeEffect(world: World, targetId: EntityId, definitionId: Long, reason: RemovalReason): Boolean {
    val effects = world.get(targetId, StatusEffects::class) ?: return false
    val removed = effects.activeEffects.filter { it.definitionId == definitionId }
    if (removed.isEmpty() || !effects.removeEffect(definitionId)) {
      return false
    }

    world.add(targetId, IsStatusValueDirty)
    removed.forEach { notifyRemoved(world, targetId, it, reason) }

    return true
  }

  /**
   * Drops [definitionId] from [bearerId] because its player asked to. Only a buff whose script allows it goes: a
   * debuff is not the bearer's to end. Returns whether it was dropped.
   */
  fun cancelByBearer(world: World, bearerId: EntityId, definitionId: Long): Boolean {
    val definition = statusEffectDefinitionRegistry.findById(definitionId) ?: return false
    val script = statusEffectScriptRegistry.get(definition.script) ?: return false
    if (definition.polarity != StatusEffectPolarity.BUFF || !script.isCancellable) {
      return false
    }

    return removeEffect(world, bearerId, definitionId, RemovalReason.CANCELLED)
  }

  /** Lifts every buff from [targetId] that its catalogue row lets a dispel lift. Returns how many went. */
  fun dispel(world: World, targetId: EntityId): Int {
    return dispel(world, targetId, potency = Int.MAX_VALUE, baseChancePercent = 100, polarities = setOf(StatusEffectPolarity.BUFF))
  }

  /**
   * Rolls once per dispellable effect of [polarities] on [targetId]: the base chance of the dispel's rank, cut by
   * a target stronger than [potency] and refused outright below a fixed spell's threshold. See
   * [Potency.dispelChancePercent]. Returns how many went.
   */
  fun dispel(
    world: World,
    targetId: EntityId,
    potency: Int,
    baseChancePercent: Int,
    polarities: Set<StatusEffectPolarity>,
  ): Int {
    val active = world.get(targetId, StatusEffects::class)?.activeEffects?.toList() ?: return 0
    val lifted = active.filter { effect ->
      val definition = statusEffectDefinitionRegistry.findById(effect.definitionId) ?: return@filter false
      if (definition.polarity !in polarities || !definition.dispellable) return@filter false

      val threshold = statusEffectScriptRegistry.get(definition.script)?.dispelThreshold ?: 0
      random.nextDouble() * 100.0 < Potency.dispelChancePercent(baseChancePercent, potency, effect.potency, threshold)
    }

    return lifted.count { removeEffect(world, targetId, it.definitionId, RemovalReason.DISPELLED) }
  }

  /** Runs the script's [StatusEffectScript.onRemove] for an effect already taken off its bearer. */
  fun notifyRemoved(world: World, hostId: EntityId, effect: ActiveStatusEffect, reason: RemovalReason) {
    if (!world.isAlive(hostId)) {
      return
    }

    scriptOf(effect)?.onRemove(hookContext(world, hostId, effect), reason)
  }

  /** An untyped hit of [amount] from nobody in particular, for a caller that only has a number. */
  fun onDamageTaken(world: World, targetId: EntityId, amount: Int): Int {
    return onDamageTaken(world, targetId, IncomingDamage.DamageAmount(amount, targetId))
  }

  override fun onDamageTaken(world: World, targetId: EntityId, hit: IncomingDamage.DamageAmount): Int {
    val active = world.get(targetId, StatusEffects::class)?.activeEffects ?: return hit.amount

    // Copied, because a freeze removes itself from the list it is being asked from.
    return active.toList().fold(hit.amount) { landing, effect ->
      scriptOf(effect)?.onDamageTaken(hookContext(world, targetId, effect), hit.copy(amount = landing)) ?: landing
    }
  }

  override fun onDamageDealt(world: World, sourceId: EntityId, landed: Int) {
    if (landed <= 0 || world.get(sourceId, StatusEffects::class)?.hasEffect(StatusEffectId.LIFESTEAL.id) != true) {
      return
    }

    Healing.heal(world, sourceId, landed * Lifesteal.HEAL_PERCENT_OF_DAMAGE / 100)
  }

  override fun notifyActed(world: World, entityId: EntityId, action: EntityAction) {
    val active = world.get(entityId, StatusEffects::class)?.activeEffects ?: return

    active.toList().forEach { effect -> scriptOf(effect)?.onActed(hookContext(world, entityId, effect), action) }
  }

  /** A harmful effect is harm like a blow, so the gate that stops a blow stops it too. */
  private fun isTurnedAway(
    world: World,
    definition: StatusEffectDefinition,
    sourceEntityId: EntityId?,
    targetId: EntityId
  ): Boolean {
    return definition.polarity == StatusEffectPolarity.DEBUFF &&
        DamageGate.verdict(world, sourceEntityId, targetId) != DamageGate.Verdict.ADMITTED
  }

  /** Only harm is turned away: a help never needed it, and an aura applied from a system need not read Nature. */
  private fun isImmune(world: World, definition: StatusEffectDefinition, targetId: EntityId): Boolean {
    if (definition.polarity != StatusEffectPolarity.DEBUFF) return false

    val immunities = world.get(targetId, Nature::class)?.statusImmunities ?: return false

    return immunities.any { it.id == definition.id }
  }

  private fun resistanceOf(
    world: World,
    definition: StatusEffectDefinition,
    targetId: EntityId,
    sourceEntityId: EntityId?,
    potency: Int?,
  ): Double {
    if (definition.resist.isEmpty()) {
      return 0.0
    }

    val combat = world.get(targetId, CombatStats::class)?.values
    val attributes = combat?.attributes
    val statSum = attributes?.let { values -> definition.resist.sumOf { it.valueIn(values) } } ?: 0

    // A cast weighs the target's Resolve against its own Potency; anything else, like a swing's proc, the levels.
    if (potency != null) {
      val resolve = Potency.resolve(
        level = world.get(targetId, Level::class)?.level ?: 1,
        intelligence = attributes?.intelligence ?: 0,
        willpower = attributes?.willpower ?: 0,
        focus = combat?.potencyFocus ?: 0,
      )

      return StatusResistance.ofPotency(statSum, resolve, potency)
    }
    val targetLevel = world.get(targetId, Level::class)?.level
    val sourceLevel = sourceEntityId?.let { world.get(it, Level::class)?.level }
    val levelLead = if (targetLevel != null && sourceLevel != null) targetLevel - sourceLevel else 0

    return StatusResistance.of(statSum, levelLead)
  }

  private fun scriptOf(effect: ActiveStatusEffect): StatusEffectScript? {
    val definition = statusEffectDefinitionRegistry.findById(effect.definitionId) ?: return null

    return statusEffectScriptRegistry.get(definition.script)
  }

  private fun hookContext(world: World, hostId: EntityId, effect: ActiveStatusEffect): StatusEffectHookContext {
    return StatusEffectHookContext(world, hostId, effect, this)
  }

  /**
   * Preferred overload for server-side call sites: the effect is named, not a magic id, and
   * [net.bestia.zone.battle.status.StatusEffectCatalogBootValidator] guarantees the constant
   * resolves against `status_effects.yml`. The `Long` overload stays for ids that only exist as
   * data at runtime (a skill script's `Buff.effectId`, a wire message).
   */
  fun applyEffect(
    world: World,
    targetId: EntityId,
    effect: StatusEffectId,
    level: Int,
    sourceEntityId: EntityId? = null,
    durationSeconds: Double? = null,
    amount: Int = 0,
    potency: Int = 0,
  ) = applyEffect(world, targetId, effect.id, level, sourceEntityId, durationSeconds, amount, potency)

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
