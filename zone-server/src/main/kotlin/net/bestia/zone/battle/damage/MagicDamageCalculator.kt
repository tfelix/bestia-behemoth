package net.bestia.zone.battle.damage

import net.bestia.zone.battle.EntityBattleContext
import java.util.Random
import kotlin.math.max

/**
 * Magic damage: the same [BaseDamageCalculator] shape as the two physical calculators, off `MATK` and
 * `SoftMDEF` instead of `ATK` and `SoftDEF`.
 *
 * **Nothing uses this yet.** No weapon is magic ([net.bestia.zone.battle.skill.AttackStrategyFactory] routes
 * `MAGIC` to melee), and the magic skills that exist - `Firebolt`, `Ember`, `Heal` - each compute their own
 * number in their own script. It is implemented rather than left as a stub so that the day a skill wants the
 * shared formula, the formula is there and is the same one everything else uses; and because a `TODO()` in a
 * damage calculator is a `NotImplementedError` waiting on the tick thread.
 */
class MagicDamageCalculator(
  random: Random
) : BaseDamageCalculator(random) {

  /** `MATK = BaseLv/4 + INT + WIL/5`. */
  override fun getStatusAttack(battleCtx: EntityBattleContext): Float =
    battleCtx.attacker.derivedStatusValues.matk.toFloat()

  override fun getBonusAttack(battleCtx: EntityBattleContext): Float =
    battleCtx.damageVariables.attackMagicBonus

  /** A spell carries no ammunition. */
  override fun getAmmoAttack(battleCtx: EntityBattleContext): Float = 0f

  /**
   * `SoftMDEF + WIL/4`: the documented magic defence (see
   * [net.bestia.zone.battle.status.DefenseValues]) plus willpower, which is what resisting a spell is and has
   * no place in the physical term.
   */
  override fun getSoftDefense(battleCtx: EntityBattleContext): Float {
    val defender = battleCtx.defender

    return max(0f, defender.defense.magicDefense + defender.statusValues.willpower / 4f)
  }

  /** Reads [DamageVariables.magicDefenseMod]; there is no equipment MDEF to reduce a spell by yet. */
  override fun getHardDefenseModifier(battleCtx: EntityBattleContext): Float =
    magicDefenseModifier(battleCtx)

  override fun getAttackModifier(battleCtx: EntityBattleContext): Float =
    max(0f, battleCtx.damageVariables.attackMagicMod)

  /** A spell's power is its own; a staff held while casting adds nothing until equipment exists. */
  override fun calculateWeaponAtk(battleCtx: EntityBattleContext): Float =
    battleCtx.weapon.matk.toFloat()
}
