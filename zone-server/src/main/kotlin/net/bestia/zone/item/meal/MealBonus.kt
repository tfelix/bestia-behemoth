package net.bestia.zone.item.meal

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValueRecalcContext

/**
 * What a meal's [net.bestia.zone.battle.status.scripts.Satisfied] does to its eater while it lasts, as written under
 * `bonus:` in `meals.yml`. Every key is a field here, so a typo fails the boot rather than granting nothing.
 *
 * Percent fields are percentage points; [hpRegen] and [manaRegen] are flat points per regeneration tick.
 */
data class MealBonus(
  val staminaRegenPercent: Int = 0,
  val hpRegen: Int = 0,
  val manaRegen: Int = 0,
  /** HP and mana keep regenerating in combat. */
  val regenerateInCombat: Boolean = false,

  val strength: Int = 0,
  val intelligence: Int = 0,
  val vitality: Int = 0,
  val dexterity: Int = 0,
  val willpower: Int = 0,
  val agility: Int = 0,
  /** Added to each of the six attributes. */
  val allAttributes: Int = 0,

  val hit: Int = 0,
  val flee: Int = 0,
  val crit: Int = 0,
  val maxHpPercent: Int = 0,
  val maxManaPercent: Int = 0,
  val healReceivedPercent: Int = 0,
  val aspdPercent: Int = 0,
  val castTimeReduction: Int = 0,

  /** Less stamina a skill costs. */
  val staminaCostReduction: Int = 0,
  /** Less stamina the weather, the road and work drain. */
  val staminaDrainReduction: Int = 0,
  val carryCapacityPercent: Int = 0,
  val offRoadSpeedPercent: Int = 0,
  val noticeRangeCutPercent: Int = 0,
  val blockChancePercent: Int = 0,
  val leechPermille: Int = 0,
  /** Taming: points added to the catch chance. */
  val catchChancePoints: Int = 0,

  val elementResistance: Map<Element, Int> = emptyMap(),
  /** More damage dealt against targets of an element. */
  val elementDamage: Map<Element, Int> = emptyMap(),
  val statusResistance: Map<StatusEffectId, Int> = emptyMap(),
) {

  fun applyTo(context: StatusValueRecalcContext) {
    context.addStaminaRegen(percent = staminaRegenPercent)
    context.addHpRegen(flat = hpRegen)
    context.addManaRegen(flat = manaRegen)
    if (regenerateInCombat) {
      context.regenerateInCombat()
    }

    context.strength += strength + allAttributes
    context.intelligence += intelligence + allAttributes
    context.vitality += vitality + allAttributes
    context.dexterity += dexterity + allAttributes
    context.willpower += willpower + allAttributes
    context.agility += agility + allAttributes

    context.addAccuracy(hit = hit, flee = flee, crit = crit)
    context.addMaxPools(hpPercent = maxHpPercent, manaPercent = maxManaPercent)
    context.addPercent(aspd = aspdPercent, healReceived = healReceivedPercent)
    if (castTimeReduction != 0) {
      context.addCastTimeReduction(castTimeReduction)
    }

    context.staminaCostReduction += staminaCostReduction
    context.staminaDrainReduction += staminaDrainReduction
    context.carryCapacityPercent += carryCapacityPercent
    context.offRoadSpeedPercent += offRoadSpeedPercent
    context.noticeRangeCutPercent += noticeRangeCutPercent
    context.blockChancePercent += blockChancePercent
    context.leechPermille += leechPermille
    context.catchChancePoints += catchChancePoints

    elementResistance.forEach { (element, percent) -> context.addElementResistance(element, percent) }
    elementDamage.forEach { (element, percent) -> context.addDamageDealt(percent, element = element) }
    statusResistance.forEach { (effect, percent) -> context.addStatusResistance(effect, percent) }
  }
}
