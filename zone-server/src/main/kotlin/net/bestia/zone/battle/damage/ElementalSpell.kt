package net.bestia.zone.battle.damage

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ElementModifier
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.util.clamp

/**
 * How every spell lands, whatever computed its power: RO pre-renewal's magic chain of element table, resistance,
 * worn MDEF as a share and attribute MDEF subtracted, never below one. Never missing or critting is
 * [net.bestia.zone.battle.attack.MagicAttackStrategy]'s half.
 */
object ElementalSpell {

  /**
   * The amount a spell of [raw] power moves [targetElement]'s health by: harm, or heal where the element table says
   * this element restores that body (see [ElementModifier.heals]).
   *
   * @param targetHardMagicDefense worn MDEF, percentage points
   * @param targetMagicDefense attribute MDEF, subtracted last
   */
  fun damage(
    raw: Float,
    element: Element,
    targetElement: Element,
    targetResistance: Map<Element, Int>,
    targetMagicDefense: Int,
    targetHardMagicDefense: Int = 0,
  ): Int {
    val resisted = (targetResistance[element.base] ?: 0).coerceIn(0, 100)
    val weighed = raw * ElementModifier.weightOf(element, targetElement) * (100 - resisted) / 100f
    val worn = hardFactor(targetHardMagicDefense)

    return (weighed * worn - targetMagicDefense).toInt().coerceAtLeast(MIN_DAMAGE)
  }

  /**
   * The same against a fight snapshot. [element] is the attack's own unless the spell always strikes as one (Holy
   * Light). [ignoreDefense] drops both MDEF terms, as a defence-piercing spell does.
   */
  fun damage(
    raw: Float,
    battle: EntityBattleContext,
    ignoreDefense: Boolean = false,
    element: Element = battle.usedAttack.attackElement,
  ): Int {
    val defender = battle.defender

    return damage(
      raw,
      element,
      defender.assumedElement,
      defender.elementResistance,
      targetMagicDefense = if (ignoreDefense) 0 else defender.defense.magicDefense,
      targetHardMagicDefense = if (ignoreDefense) 0 else defender.defense.hardMagicDefense,
    )
  }

  /** [damage] as the blow it is: a [Heal] where the element restores the target, a [HitDamage] otherwise. */
  fun land(
    raw: Float,
    battle: EntityBattleContext,
    ignoreDefense: Boolean = false,
    element: Element = battle.usedAttack.attackElement,
  ): Damage {
    val amount = damage(raw, battle, ignoreDefense, element)

    if (ElementModifier.heals(element, battle.defender.assumedElement)) {
      return Heal(amount)
    }

    return HitDamage(amount)
  }

  /** A spell of no element at all, which only MDEF stands against: Soul Break. */
  fun elementless(raw: Float, battle: EntityBattleContext): Int {
    val defense = battle.defender.defense

    return (raw * hardFactor(defense.hardMagicDefense) - defense.magicDefense).toInt().coerceAtLeast(MIN_DAMAGE)
  }

  /** `(100 - MDEF) / 100`, bounded like the physical one: armour never makes anybody immune. */
  private fun hardFactor(hardMagicDefense: Int): Float {
    return ((100 - hardMagicDefense) / 100f).clamp(MIN_WORN_SHARE, MAX_WORN_SHARE)
  }

  private const val MIN_DAMAGE = 1
  private const val MIN_WORN_SHARE = 0.05f
  private const val MAX_WORN_SHARE = 4f
}
