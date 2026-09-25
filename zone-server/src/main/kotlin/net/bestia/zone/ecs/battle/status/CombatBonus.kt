package net.bestia.zone.ecs.battle.status

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.Component

/**
 * Everything adding to this entity's attack power and subtracting from what reaches it, resolved into four
 * numbers - worn equipment today, and whatever else learns to contribute tomorrow.
 *
 * [atk] and [matk] feed `Weapon` and from there the `weaponAtk` term in
 * `net.bestia.zone.battle.damage.BaseDamageCalculator`; [hardDefense] and [hardMagicDefense] are Ragnarok
 * Online's *equipment* defence, a percentage reduction applied as a multiplier, as distinct from the flat,
 * attribute-derived soft defence `net.bestia.zone.battle.status.DefenseValues` computes. A critical hit
 * ignores this component's two defence terms and not the soft one - see `BaseDamageCalculator`.
 *
 * Named for what it is rather than `EquipmentBonus`: nothing here is structurally tied to gear, and a
 * passive or a status effect that wanted to grant flat attack would contribute through the same fields.
 *
 * Written **only** by `net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem`, via [copyFrom]; read by
 * `net.bestia.zone.battle.BattleContextFactory` while it projects an entity into a `BattleEntity`.
 *
 * Absent means unmodified, and it overwrites rather than accumulates, both for the reasons
 * [RegenerationModifiers] sets out at length - it is the same shape, with the same single writer and the
 * same dependence on `IsStatusValueDirty` to keep it from going stale.
 *
 * Server-side bookkeeping only, deliberately not [net.bestia.zone.ecs.core.Dirtyable]. The client reads the
 * consequence off the damage numbers it already receives; showing ATK and DEF in the status window is a
 * separate change, and would want the resolved totals rather than this contribution alone.
 */
class CombatBonus(
  var atk: Int = 0,
  var matk: Int = 0,
  var hardDefense: Int = 0,
  var hardMagicDefense: Int = 0
) : Component {

  fun copyFrom(context: StatusValueRecalcContext) {
    atk = context.attack
    matk = context.magicAttack
    hardDefense = context.hardDefense
    hardMagicDefense = context.hardMagicDefense
  }
}
