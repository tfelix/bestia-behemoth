package net.bestia.zone.item.script

import net.bestia.zone.battle.Healing
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.status.CurMax
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId

/**
 * Food, drink and potions: everything whose effect is a number added to a vital, an ailment washed out, or a
 * status effect taken on - an Alchemist's antidote, swiftness potion or elixir.
 *
 * Still one class per item, because [ItemScript] binds by id - but the numbers are the only thing that
 * differs between a loaf and a wheel of cheese, so they are all a subclass names.
 *
 * A use succeeds when at least one named vital was actually moved, an ailment it [cures] was there to wash out, or
 * an effect it [grants] took hold. That is what lets a meal restoring both feed a Master who has no [Health] at
 * all, while a potion with nothing to heal and an antidote with no poison to clear stay in the bag.
 *
 * @param messages needed whenever [health] is restored: the number over the user's head is how a player
 *   sees a heal happen
 * @param isPotion a drink that works on whoever it reaches, so a Potion Pitcher may throw it at an ally
 * @param cures the ailments it washes out
 * @param grants the effects it lays on its drinker, each at its level
 * @param statusEffects needed whenever it [cures] or [grants] anything
 */
abstract class RestorativeScript(
  override val itemId: Long,
  val health: Int = 0,
  val stamina: Int = 0,
  private val mana: Int = 0,
  private val messages: OutMessageProcessor? = null,
  val isPotion: Boolean = false,
  private val cures: Set<StatusEffectId> = emptySet(),
  private val grants: List<Grant> = emptyList(),
  private val statusEffects: StatusEffectService? = null,
) : ItemScript {

  /** An effect a drink lays on, at [level]. */
  data class Grant(val effect: StatusEffectId, val level: Int)

  init {
    require(health == 0 || messages != null) {
      "Item $itemId restores health but has nothing to show it with"
    }
    require((cures.isEmpty() && grants.isEmpty()) || statusEffects != null) {
      "Item $itemId cures or grants an effect but has no status effects to do it with"
    }
  }

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    return restore(world, userId, FULL_STRENGTH_PERCENT)
  }

  /**
   * Restores [drinkerId] by [strengthPercent] of this item's amounts, as a use does at 100. The cures and the effects
   * it grants are whole whatever the strength: half an antidote is not a thing.
   */
  fun restore(world: World, drinkerId: EntityId, strengthPercent: Int): Boolean {
    val healing = health * strengthPercent / 100
    // Through Healing, so sickness halves a potion too and it closes a bleeding wound.
    val healed = healing != 0 && world.has(drinkerId, Health::class)
    if (healed) {
      Healing.heal(world, drinkerId, healing)
    }
    val fed = refill(world.get(drinkerId, Stamina::class), stamina * strengthPercent / 100)
    val refilled = refill(world.get(drinkerId, Mana::class), mana * strengthPercent / 100)
    val cured = cure(world, drinkerId)
    val granted = grant(world, drinkerId)

    if (!healed && !fed && !refilled && !cured && !granted) {
      return false
    }

    if (healed) {
      showHeal(world, drinkerId, healing)
    }

    return true
  }

  /** Whether the vital moved: one this item says nothing about, or one the user has not got, did not. */
  private fun refill(vital: CurMax?, amount: Int): Boolean {
    if (vital == null || amount == 0) {
      return false
    }

    // CurMax.current clamps to [0, max] itself, so using this while full simply wastes it.
    vital.current += amount

    return true
  }

  /** Whether any ailment it [cures] was there to wash out. */
  private fun cure(world: World, drinkerId: EntityId): Boolean {
    if (cures.isEmpty()) return false
    val effects = world.get(drinkerId, StatusEffects::class) ?: return false
    val present = cures.filter { effects.hasEffect(it.id) }

    present.forEach { statusEffects!!.removeEffect(world, drinkerId, it.id, RemovalReason.DISPELLED) }

    return present.isNotEmpty()
  }

  /** Whether any effect it [grants] took hold. */
  private fun grant(world: World, drinkerId: EntityId): Boolean {
    return grants.map { statusEffects!!.applyEffect(world, drinkerId, it.effect.id, it.level) }.any { it }
  }

  private fun showHeal(world: World, drinkerId: EntityId, amount: Int) {
    messages?.sendToObserversOf(world, drinkerId, DamageEntitySMSG.fromItemHeal(drinkerId, amount))
  }

  private companion object {
    const val FULL_STRENGTH_PERCENT = 100
  }
}
