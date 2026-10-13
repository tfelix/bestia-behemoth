package net.bestia.zone.battle.status

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
        weapon = Weapon(atk = context.attack, matk = context.magicAttack, upgradeLevel = context.weaponUpgradeLevel),
        hardDefense = context.hardDefense,
        hardMagicDefense = context.hardMagicDefense
      )
    }

    private fun resolve(
      level: Int,
      base: StatusValues,
      attributes: StatusValues,
      weapon: Weapon,
      hardDefense: Int,
      hardMagicDefense: Int
    ): CombatValues {
      return CombatValues(
        base = base,
        attributes = attributes,
        derived = DerivedStatusValues.fromStatusValues(level, attributes),
        defense = DefenseValues.fromStatusValues(level, attributes, hardDefense, hardMagicDefense),
        weapon = if (weapon == Weapon.NONE) Weapon.NONE else weapon
      )
    }
  }
}
