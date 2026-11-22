package net.bestia.zone.battle.damage

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ElementModifier
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.util.clamp

/**
 * How every spell lands, whatever computed its power: a bolt, an ultimate, one tick of a burning area, a mage's
 * basic attack. RO pre-renewal's magic chain - weighed by the element table and the target's resistance to the
 * element, then the worn MDEF as a percentage, then the attribute MDEF subtracted, and never below one.
 *
 * A spell never misses and never crits; that is [net.bestia.zone.battle.attack.MagicAttackStrategy]'s half. This is
 * the arithmetic each spell script used to repeat with a different subset of the steps.
 */
object ElementalSpell {

  /**
   * @param raw the spell's power before the target is considered
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
    val weighed = raw * ElementModifier.getModifierFloat(element, targetElement) * (100 - resisted) / 100f
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

  /**
   * A spell of no element at all - it strikes the soul, not the body - so only MDEF stands in its way: Soul Break.
   */
  fun elementless(raw: Float, battle: EntityBattleContext): Int {
    val defense = battle.defender.defense

    return (raw * hardFactor(defense.hardMagicDefense) - defense.magicDefense).toInt().coerceAtLeast(MIN_DAMAGE)
  }

  /** `(100 - MDEF) / 100`, bounded like the physical one: armour never makes anybody immune. */
  private fun hardFactor(hardMagicDefense: Int): Float =
    ((100 - hardMagicDefense) / 100f).clamp(MIN_WORN_SHARE, MAX_WORN_SHARE)

  private const val MIN_DAMAGE = 1
  private const val MIN_WORN_SHARE = 0.05f
  private const val MAX_WORN_SHARE = 4f
}
