package net.bestia.zone.battle.damage

import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.Unholy

/**
 * Percent bonuses to the damage an entity deals and reductions to the damage it takes, by what is on the other
 * side of the hit. Ragnarok Online pre-renewal's card modifiers (rAthena `battle_calc_cardfix`): Demon Bane's
 * "+5% against Demons and the Undead", Iron Skin's "-3% physical damage taken", a card's "-30% from demi-humans".
 *
 * They weigh the final number, after defence, and a critical hit too, since a crit only ignores defence. Bonuses
 * in one category add up: two "-30% from demi-humans" make -60%. Categories multiply: -30% from demi-humans and
 * -20% melee taken make 0.7 x 0.8. A reduction of 100% or more in a category stops the hit outright. A bonus against
 * one target, like Pack Hunt's against a marked prey, is a category of its own.
 *
 * The element the defender resists is not here: that is `elementResistance`, which the formula already weighs.
 */
data class DamageModifiers(
  val dealtVsKind: Map<BestiaKind, Int> = emptyMap(),
  val dealtVsElement: Map<Element, Int> = emptyMap(),
  val dealtVsBoss: Int = 0,
  /** One category for all of [Unholy], so an Undead of Undead element is not counted twice. */
  val dealtVsUnholy: Int = 0,
  val takenFromKind: Map<BestiaKind, Int> = emptyMap(),
  val takenFromBoss: Int = 0,
  val takenFromUnholy: Int = 0,
  val takenByForm: Map<HitForm, Int> = emptyMap(),
  val takenByReach: Map<HitReach, Int> = emptyMap(),
  /** A ward against every blow and spell, like Magic Armor: a category of its own, as RO's Energy Coat is. */
  val takenFromAnyHit: Int = 0,
) {

  /** One side of a hit as the modifiers see it. [kind] is null for what has none, like a prop. */
  data class Side(
    val modifiers: DamageModifiers,
    val kind: BestiaKind?,
    val element: Element,
    val boss: Boolean,
  ) {
    val isUnholy: Boolean
      get() {
        return Unholy.isUnholy(kind, element)
      }
  }

  companion object {
    val NONE = DamageModifiers()

    /**
     * [amount] after the attacker's bonuses and the defender's reductions. An untyped hit passes unchanged.
     * [dealtVsTargetPercent] is the attacker's bonus against this defender alone.
     */
    fun weigh(amount: Int, kind: HitKind, attacker: Side?, defender: Side, dealtVsTargetPercent: Int = 0): Int {
      if (kind.form == HitForm.OTHER || amount <= 0) {
        return amount
      }

      // Per mille in whole numbers, as rAthena does, so 0.7 x 0.8 is exactly 560 and not 559.99.
      var factor = WHOLE
      if (attacker != null) {
        val dealt = attacker.modifiers
        factor = factor.scaledBy(100 + (defender.kind?.let { dealt.dealtVsKind[it] } ?: 0))
        factor = factor.scaledBy(100 + (dealt.dealtVsElement[defender.element.base] ?: 0))
        factor = factor.scaledBy(100 + if (defender.boss) dealt.dealtVsBoss else 0)
        factor = factor.scaledBy(100 + if (defender.isUnholy) dealt.dealtVsUnholy else 0)
        factor = factor.scaledBy(100 + dealtVsTargetPercent)
      }

      val taken = defender.modifiers
      factor = factor.scaledBy(100 - (attacker?.kind?.let { taken.takenFromKind[it] } ?: 0))
      factor = factor.scaledBy(100 - if (attacker?.boss == true) taken.takenFromBoss else 0)
      factor = factor.scaledBy(100 - if (attacker?.isUnholy == true) taken.takenFromUnholy else 0)
      factor = factor.scaledBy(100 - (taken.takenByForm[kind.form] ?: 0))
      factor = factor.scaledBy(100 - (taken.takenByReach[kind.reach] ?: 0))
      factor = factor.scaledBy(100 - taken.takenFromAnyHit)

      return (amount.toLong() * factor / WHOLE).toInt()
    }

    private const val WHOLE = 1000L

    private fun Long.scaledBy(percent: Int): Long = this * percent.coerceAtLeast(0) / 100
  }
}
