package net.bestia.zone.item.stat

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.status.StatusValueRecalcContext

/**
 * A stat an item can carry, fixed in `items.yml` or rolled when the item is made.
 *
 * Every stat is priced in points, and one point is what one attribute point yields: 1 STR is 1 point, and
 * so is +2 ATK, because the damage formula doubles the attribute term. That price is [unitsPerPoint], and it
 * is the only thing [ItemLevel] needs to know about a stat. See the docs' forging page for the table.
 */
enum class ItemStat(
  val unitsPerPoint: Double,
  private val effect: (StatusValueRecalcContext, Int) -> Unit
) {
  STR(1.0, { c, v -> c.strength += v }),
  AGI(1.0, { c, v -> c.agility += v }),
  VIT(1.0, { c, v -> c.vitality += v }),
  INT(1.0, { c, v -> c.intelligence += v }),
  DEX(1.0, { c, v -> c.dexterity += v }),
  WIL(1.0, { c, v -> c.willpower += v }),
  ATK(2.0, { c, v -> c.addAttack(atk = v) }),
  MATK(2.0, { c, v -> c.addAttack(matk = v) }),
  HIT(1.0, { c, v -> c.addAccuracy(hit = v) }),
  FLEE(1.0, { c, v -> c.addAccuracy(flee = v) }),
  CRIT(1.0 / 3, { c, v -> c.addAccuracy(crit = v) }),
  MAX_HP(1.0, { c, v -> c.addMaxPools(hpPercent = v) }),
  MAX_MANA(1.0, { c, v -> c.addMaxPools(manaPercent = v) }),
  DEF(1.0, { c, v -> c.addDefense(hardDefense = v) }),
  MDEF(1.0, { c, v -> c.addDefense(hardMagicDefense = v) }),
  HP_REGEN(5.0, { c, v -> c.addHpRegen(percent = v) }),
  MANA_REGEN(5.0, { c, v -> c.addManaRegen(percent = v) }),
  STAMINA_REGEN(5.0, { c, v -> c.addStaminaRegen(percent = v) }),
  CAST_TIME(1.0, { c, v -> c.addCastTimeReduction(v) }),
  RESIST_NORMAL(3.0, resist(Element.NORMAL)),
  RESIST_WATER(3.0, resist(Element.WATER)),
  RESIST_EARTH(3.0, resist(Element.EARTH)),
  RESIST_FIRE(3.0, resist(Element.FIRE)),
  RESIST_WIND(3.0, resist(Element.WIND)),
  RESIST_POISON(3.0, resist(Element.POISON)),
  RESIST_HOLY(3.0, resist(Element.HOLY)),
  RESIST_SHADOW(3.0, resist(Element.SHADOW)),
  RESIST_GHOST(3.0, resist(Element.GHOST)),
  RESIST_UNDEAD(3.0, resist(Element.UNDEAD));

  fun applyTo(context: StatusValueRecalcContext, value: Int) {
    effect(context, value)
  }

  fun pointsOf(value: Int): Double {
    return value / unitsPerPoint
  }
}

private fun resist(element: Element): (StatusValueRecalcContext, Int) -> Unit {
  return { c, v -> c.addElementResistance(element, v) }
}
