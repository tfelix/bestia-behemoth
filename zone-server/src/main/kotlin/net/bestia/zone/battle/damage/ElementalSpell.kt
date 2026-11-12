package net.bestia.zone.battle.damage

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ElementModifier

/**
 * How a Wizard's elemental spell lands, a bolt or one tick of an area: weighed by the element table and the
 * target's resistance to the element, less its MDEF, and never below one.
 */
object ElementalSpell {

  fun damage(
    raw: Float,
    element: Element,
    targetElement: Element,
    targetResistance: Map<Element, Int>,
    targetMagicDefense: Int,
  ): Int {
    val resisted = (targetResistance[element.base] ?: 0).coerceIn(0, 100)
    val weighed = raw * ElementModifier.getModifierFloat(element, targetElement) * (100 - resisted) / 100f

    return (weighed - targetMagicDefense).toInt().coerceAtLeast(MIN_DAMAGE)
  }

  private const val MIN_DAMAGE = 1
}
