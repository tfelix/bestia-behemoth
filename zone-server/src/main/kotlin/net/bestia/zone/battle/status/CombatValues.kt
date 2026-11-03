package net.bestia.zone.battle.status

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.Weapon

/**
 * What a fight reads of an entity, resolved by `StatusValueRecalcSystem` whenever an attribute, level, worn item or
 * effect changes instead of being rebuilt per swing. Valid for the level it was resolved at.
 *
 * Immutable so it can be shared: every unbuffed mob of a species resolves to an equal value, which
 * [CombatValuesPool] turns into one instance.
 */
data class CombatValues(
  /** Permanent attributes - a species', or a master's invested ones. What the recalc starts from. */
  val base: StatusValues,

  /** [base] with passives, gear and effects applied; the same instance as [base] when none apply. */
  val attributes: StatusValues,

  val derived: DerivedStatusValues,

  val defense: DefenseValues,

  /** [Weapon.NONE] when bare-handed. */
  val weapon: Weapon,

  /** Percentage points of damage removed per base element, see [Element.base]. */
  val elementResistance: Map<Element, Int> = emptyMap(),

  /** Percentage points taken off every cast time. */
  val castTimeReduction: Int = 0,

  /** See [StatusValueRecalcContext.elementOverride]. */
  val elementOverride: Element? = null,

  /** Percentage points on top of the attack speed, see [AttackSpeed.delaySeconds]. */
  val aspdPercent: Int = 0,
) {

  companion object {

    /** What a promoted prop or a construction site has: attackable, never levelling or learning. */
    val OBJECT_BASELINE: CombatValues = ofBase(
      level = 1,
      base = StatusValues(strength = 1, vitality = 1, intelligence = 1, agility = 1, willpower = 1, dexterity = 1)
    )

    /**
     * Wearing and suffering nothing: the spawn seed, and an unbuffed mob's value for life. Equals [fromContext] on an
     * empty context, which a test pins so the seed cannot drift from the recalc.
     */
    fun ofBase(level: Int, base: StatusValues): CombatValues {
      return resolve(level, base, base, Weapon.NONE, hardDefense = 0, hardMagicDefense = 0)
    }

    fun fromContext(level: Int, base: StatusValues, context: StatusValueRecalcContext): CombatValues {
      val resolved = StatusValues(
        strength = context.strength,
        vitality = context.vitality,
        intelligence = context.intelligence,
        agility = context.agility,
        willpower = context.willpower,
        dexterity = context.dexterity
      )

      return resolve(
        level = level,
        base = base,
        attributes = if (resolved == base) base else resolved,
        weapon = Weapon(
          atk = context.attack,
          matk = context.magicAttack,
          upgradeLevel = context.weaponUpgradeLevel,
          ranged = context.rangedWeapon
        ),
        hardDefense = context.hardDefense,
        hardMagicDefense = context.hardMagicDefense,
        hitBonus = context.hit,
        fleeBonus = context.flee,
        critBonus = context.crit,
        elementResistance = context.elementResistance,
        castTimeReduction = context.castTimeReduction,
        elementOverride = context.elementOverride,
        defensePercent = context.defensePercent,
        hitPercent = context.hitPercent,
        aspdPercent = context.aspdPercent
      )
    }

    private fun resolve(
      level: Int,
      base: StatusValues,
      attributes: StatusValues,
      weapon: Weapon,
      hardDefense: Int,
      hardMagicDefense: Int,
      hitBonus: Int = 0,
      fleeBonus: Int = 0,
      critBonus: Int = 0,
      elementResistance: Map<Element, Int> = emptyMap(),
      castTimeReduction: Int = 0,
      elementOverride: Element? = null,
      defensePercent: Int = 0,
      hitPercent: Int = 0,
      aspdPercent: Int = 0
    ): CombatValues {
      val derived = DerivedStatusValues.fromStatusValues(level, attributes, hitBonus, fleeBonus, critBonus)
      val defense = DefenseValues.fromStatusValues(level, attributes, hardDefense, hardMagicDefense)

      return CombatValues(
        base = base,
        attributes = attributes,
        derived = if (hitPercent == 0) derived else derived.copy(hitrate = scaled(derived.hitrate, hitPercent)),
        defense = if (defensePercent == 0) defense else defense.scaledBy(defensePercent),
        weapon = if (weapon == Weapon.NONE) Weapon.NONE else weapon,
        elementResistance = elementResistance,
        castTimeReduction = castTimeReduction,
        elementOverride = elementOverride,
        aspdPercent = aspdPercent
      )
    }

    private fun scaled(value: Int, percent: Int): Int {
      return (value * (100 + percent) / 100).coerceAtLeast(0)
    }
  }
}
