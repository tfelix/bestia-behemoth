package net.bestia.zone.battle.status

import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.gear.ToolKind
import net.bestia.zone.battle.damage.DamageModifiers
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

  /** Percentage points on every heal received, see `Healing`. */
  val healReceivedPercent: Int = 0,

  /** See [StatusValueRecalcContext.weaponElement]. */
  val weaponElement: Element? = null,

  /** See [DamageModifiers]. */
  val damageModifiers: DamageModifiers = DamageModifiers.NONE,

  /** See [StatusValueRecalcContext.potencyFocus]. */
  val potencyFocus: Int = 0,

  /** See [StatusValueRecalcContext.staminaCostReduction] and the fields below it. */
  val staminaCostReduction: Int = 0,
  val staminaDrainReduction: Int = 0,
  val castMovePercent: Int = 0,
  val castInterruptResistance: Int = 0,
  val statusImmunities: Set<StatusEffectId> = emptySet(),
  val knockbackImmune: Boolean = false,
  val carryCapacityPercent: Int = 0,

  /** What is held in the right hand, for a rule about it: a tree falls only to an axe or a felling tool. */
  val heldWeaponType: WeaponType? = null,
  val heldTool: ToolKind? = null,
) {

  companion object {

    /** What a promoted prop or a construction site has: attackable, never levelling or learning. */
    val OBJECT_BASELINE: CombatValues = ofBase(
      level = 1,
      base = StatusValues(strength = 1, vitality = 1, intelligence = 1, agility = 1, willpower = 1, dexterity = 1)
    )

    /** A structure whose armour is all it has: [OBJECT_BASELINE] with [hardDefense]. */
    fun ofStructure(hardDefense: Int): CombatValues {
      val base = StatusValues(strength = 1, vitality = 1, intelligence = 1, agility = 1, willpower = 1, dexterity = 1)

      return resolve(level = 1, base = base, attributes = base, weapon = Weapon.NONE, hardDefense = hardDefense, hardMagicDefense = 0)
    }

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
        defenseCutPercent = context.defenseCutPercent,
        hitPercent = context.hitPercent,
        aspdPercent = context.aspdPercent,
        healReceivedPercent = context.healReceivedPercent,
        attackPercent = context.attackPercent,
        weaponElement = context.weaponElement
      ).copy(
        damageModifiers = context.damageModifiers,
        potencyFocus = context.potencyFocus,
        staminaCostReduction = context.staminaCostReduction,
        staminaDrainReduction = context.staminaDrainReduction,
        castMovePercent = context.castMovePercent,
        castInterruptResistance = context.castInterruptResistance,
        statusImmunities = context.statusImmunities,
        knockbackImmune = context.knockbackImmune,
        carryCapacityPercent = context.carryCapacityPercent,
        heldWeaponType = context.heldWeaponType,
        heldTool = context.heldTool,
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
      defenseCutPercent: Int = 0,
      hitPercent: Int = 0,
      aspdPercent: Int = 0,
      healReceivedPercent: Int = 0,
      attackPercent: Int = 0,
      weaponElement: Element? = null
    ): CombatValues {
      val derived = DerivedStatusValues.fromStatusValues(level, attributes, hitBonus, fleeBonus, critBonus)
      val defense = DefenseValues.fromStatusValues(level, attributes, hardDefense, hardMagicDefense)

      return CombatValues(
        base = base,
        attributes = attributes,
        derived = derived.copy(
          hitrate = scaled(derived.hitrate, hitPercent),
          atk = scaled(derived.atk, attackPercent),
          rangedAtk = scaled(derived.rangedAtk, attackPercent),
          matk = scaled(derived.matk, attackPercent)
        ),
        defense = defense
          .let { if (defensePercent == 0) it else it.withHardDefenseRaisedBy(defensePercent) }
          .let { if (defenseCutPercent == 0) it else it.withPhysicalDefenseCutBy(defenseCutPercent) },
        weapon = when {
          weapon == Weapon.NONE -> Weapon.NONE
          attackPercent == 0 -> weapon
          else -> weapon.copy(atk = scaled(weapon.atk, attackPercent), matk = scaled(weapon.matk, attackPercent))
        },
        elementResistance = elementResistance,
        castTimeReduction = castTimeReduction,
        elementOverride = elementOverride,
        aspdPercent = aspdPercent,
        healReceivedPercent = healReceivedPercent,
        weaponElement = weaponElement
      )
    }

    private fun scaled(value: Int, percent: Int): Int {
      return (value * (100 + percent) / 100).coerceAtLeast(0)
    }
  }
}
