package net.bestia.zone.battle.status

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.status.BaseStatusValues

/**
 * Mutable working set a [StatusEffectScript] writes into while
 * [net.bestia.zone.battle.ecs.effects.StatusValueRecalcSystem] rebuilds an entity's effective
 * status values from scratch. Seeded from the entity's permanent attributes - a master's
 * [BaseStatusValues], a mob's species - plus whatever other base values feed
 * into the recalc (currently just [baseSpeed]); starts equal to the unbuffed values, then every
 * learned passive skill, worn item and active effect mutates it in turn.
 *
 * The regeneration modifiers are the one part not seeded from a base value: they start neutral and
 * only accumulate contributions, because the *base* regeneration rate is derived separately by
 * [RegenerationCalculator] from the pool size and attributes at the moment regen actually ticks.
 * They are exposed as [RegenModifier] values with `private set` plus `add*` methods rather than as
 * mutable fields, so one script cannot silently discard another's contribution by assigning.
 *
 * The four combat terms - [attack], [magicAttack], [hardDefense], [hardMagicDefense] - start at zero for
 * the same reason and are protected the same way. There is no *base* weapon or equipment defence to seed
 * them from: a bare-handed fighter's power is the attribute-derived term the damage formula computes
 * separately, and armour that is not worn contributes nothing rather than a baseline.
 */
class StatusValueRecalcContext(
  base: StatusValues,
  baseSpeed: Float
) {

  /** For an owner, whose permanent attributes live in a [BaseStatusValues] component of their own. */
  constructor(base: BaseStatusValues, baseSpeed: Float) : this(base.toAttributes(), baseSpeed)

  var strength: Int = base.strength
  var intelligence: Int = base.intelligence
  var vitality: Int = base.vitality
  var dexterity: Int = base.dexterity
  var willpower: Int = base.willpower
  var agility: Int = base.agility
  var speed: Float = baseSpeed

  var attack: Int = 0
    private set

  var magicAttack: Int = 0
    private set

  var hardDefense: Int = 0
    private set

  var hardMagicDefense: Int = 0
    private set

  var hit: Int = 0
    private set

  var flee: Int = 0
    private set

  var crit: Int = 0
    private set

  /** Percentage points on top of the formula's max HP and max mana: the docs' `HPModPerc` / `ManaModPerc`. */
  var maxHpPercent: Int = 0
    private set

  var maxManaPercent: Int = 0
    private set

  /** Percentage points of damage removed per element, keyed by the element without its level. */
  var elementResistance: Map<Element, Int> = emptyMap()
    private set

  /** Percentage points taken off every cast time. */
  var castTimeReduction: Int = 0
    private set

  /** Refinement of the weapon in hand. It scales the weapon term of the damage formula, not the total. */
  var weaponUpgradeLevel: Int = 0

  var hpRegen: RegenModifier = RegenModifier()
    private set

  var manaRegen: RegenModifier = RegenModifier()
    private set

  var staminaRegen: RegenModifier = RegenModifier()
    private set

  /**
   * Adds to this entity's HP regeneration: [flat] extra points per tick, and/or [percent] percentage
   * points on top of the resolved rate. Named arguments are the point of the signature - `flat` and
   * `percent` are trivially transposed at a call site, and `addHpRegen(percent = 3 * level)` cannot
   * be misread the way a positional `3 * level` could.
   */
  fun addHpRegen(flat: Int = 0, percent: Int = 0) {
    hpRegen = hpRegen.plus(flat = flat, percent = percent)
  }

  fun addManaRegen(flat: Int = 0, percent: Int = 0) {
    manaRegen = manaRegen.plus(flat = flat, percent = percent)
  }

  fun addStaminaRegen(flat: Int = 0, percent: Int = 0) {
    staminaRegen = staminaRegen.plus(flat = flat, percent = percent)
  }

  /**
   * Adds flat attack power, the term a weapon carries.
   *
   * **Un-refined**: `Weapon.physicalAtk` already adds the refinement on top, so a script that scaled this by its
   * own upgrade level would charge for refinement twice, and the curve would then live in as many places as there
   * are weapons.
   */
  fun addAttack(atk: Int = 0, matk: Int = 0) {
    attack += atk
    magicAttack += matk
  }

  /** Adds to the docs' `HitModSum`, `FleeModSum` and the CRIT bonus, on top of the attribute-derived values. */
  fun addAccuracy(hit: Int = 0, flee: Int = 0, crit: Int = 0) {
    this.hit += hit
    this.flee += flee
    this.crit += crit
  }

  fun addMaxPools(hpPercent: Int = 0, manaPercent: Int = 0) {
    maxHpPercent += hpPercent
    maxManaPercent += manaPercent
  }

  fun addElementResistance(element: Element, percent: Int) {
    val base = element.base
    elementResistance = elementResistance + (base to (elementResistance[base] ?: 0) + percent)
  }

  fun addCastTimeReduction(percent: Int) {
    castTimeReduction += percent
  }

  /**
   * Adds equipment ("hard") defence, in percentage points of damage removed - 3 means 3% less. Ragnarok
   * Online's pre-renewal split, which the rest of the damage package already follows: this is the
   * multiplicative kind, and the flat kind is derived from attributes by
   * [net.bestia.zone.battle.status.DefenseValues] rather than contributed here.
   */
  fun addDefense(hardDefense: Int = 0, hardMagicDefense: Int = 0) {
    this.hardDefense += hardDefense
    this.hardMagicDefense += hardMagicDefense
  }
}
