package net.bestia.zone.battle.status

import net.bestia.zone.ecs.battle.status.BaseStatusValues

/**
 * Mutable working set a [StatusEffectScript] writes into while
 * [net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem] rebuilds an entity's effective
 * status values from scratch. Seeded from [BaseStatusValues] plus whatever other base values feed
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
  base: BaseStatusValues,
  baseSpeed: Float
) {
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
   * **Un-refined**: `BaseDamageCalculator.calculateWeaponAtk` already adds
   * `upgradeLevel * REFINE_ATTACK_PER_LEVEL` on top, so a script that scaled this by its own upgrade level
   * would charge for refinement twice, and the curve would then live in as many places as there are
   * weapons.
   */
  fun addAttack(atk: Int = 0, matk: Int = 0) {
    attack += atk
    magicAttack += matk
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
