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
}
