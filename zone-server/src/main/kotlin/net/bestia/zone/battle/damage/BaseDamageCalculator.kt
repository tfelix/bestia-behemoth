package net.bestia.zone.battle.damage

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.ElementModifier
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.util.clamp
import java.util.Random
import kotlin.math.floor
import kotlin.math.max

/**
 * The physical damage formula, shared by the melee and ranged calculators.
 *
 * ### Shape
 *
 * ```
 * baseAtk = 2 * statusAtk * varMod + weaponAtk * varModReduced + ammoAtk + bonusAtk
 * baseAtk = max(1, baseAtk * elementMod)
 * damage  = floor(baseAtk * atkMod * hardDefMod * critMod) - softDef
 * damage  = max(MIN_DAMAGE, damage)
 * ```
 *
 * Modelled on Ragnarok Online's pre-renewal weapon damage, which is what the rest of this package already
 * follows - `DefenseValues`, `DerivedStatusValues` and [ElementModifier] are all shaped after it. Three of its
 * rules matter more than the arithmetic:
 *
 * - **Defence comes in two kinds.** Equipment DEF is a *percentage* reduction ([getHardDefenseModifier]);
 *   attribute DEF is a *flat* subtraction applied last ([getSoftDefense]). Only the flat one exists here so
 *   far, because no equipment system does - so `hardDefMod` currently carries nothing but the
 *   [DamageVariables.physicalDefenseMod] a script or a status effect may have set.
 * - **A critical hit ignores defence entirely**, both kinds, on top of its damage bonus. That is what makes a
 *   crit worth building for against an armoured target rather than just 40% more of a small number.
 * - **A connected hit always costs at least [MIN_DAMAGE].** Whether it connected at all is not decided here -
 *   see `PhysicalAttackStrategy.isMiss`.
 *
 * ### What is missing, and why it is missing rather than approximated
 *
 * - **Weapon and ammunition attack** are read but always zero: nothing equips anything yet
 *   (`BattleContextFactory.equippedWeapon`). The terms are wired so a weapon starts mattering the day one can
 *   be held, rather than needing this formula reopened.
 * - **The size modifier.** `SizeModifier` exists and is exactly RO's table, but neither `Weapon` nor
 *   `BattleEntity` carries a [net.bestia.zone.battle.Size], so there is nothing to look up. Left out rather
 *   than defaulted to a guess.
 * - **Element** is applied, but every entity is `NORMAL` until an element component exists, so in practice the
 *   lookup returns 1.0 for now.
 * - **An element that *heals* its target.** Twenty-four entries in [ElementModifier] are negative - holy against
 *   holy, poison against undead - which in RO means the hit restores health instead of taking it. Here the
 *   `max(1f, …)` in [getBaseAttack] turns that into one point of damage, because [Damage] cannot carry a
 *   negative amount and the whole application path treats a result as a loss. Making it real means a result
 *   type, not a change to this arithmetic.
 */
abstract class BaseDamageCalculator(
  private val random: Random
) : DamageCalculator {

  /**
   * The damage this attack deals, before the caller decides what kind of hit it was.
   *
   * `damageVariables.isCriticalHit` is not a thing here: the strategy rolls the crit and passes it in through
   * [DamageVariables.criticalDamageMod] being meaningful only on a crit, so this takes it as a parameter
   * instead - see [calculateDamage].
   */
  override fun calculateDamage(battleCtx: EntityBattleContext): Int = calculateDamage(battleCtx, isCritical = false)

  /**
   * @param isCritical when true, adds the critical bonus and bypasses both defence terms, per RO.
   */
  fun calculateDamage(battleCtx: EntityBattleContext, isCritical: Boolean): Int {
    val vars = battleCtx.damageVariables

    val baseAtk = getBaseAttack(battleCtx)
    val atkMod = getAttackModifier(battleCtx)

    // A crit ignores defence, which is the whole point of one - but "ignore" has to mean *at least* neutral,
    // not exactly neutral. The same modifier also carries defence-*piercing*, above 1, and forcing it to 1
    // would make critting a stripped target deal a third of what an ordinary swing does.
    val defenseMod = getHardDefenseModifier(battleCtx)
    val hardDefMod = if (isCritical) max(1f, defenseMod) else defenseMod
    val softDef = if (isCritical) 0f else getSoftDefense(battleCtx)

    val critMod = if (isCritical) CRITICAL_DAMAGE_BONUS * max(0f, vars.criticalDamageMod) else 1f

    val damage = floor(baseAtk * atkMod * hardDefMod * critMod) - softDef

    LOG.trace {
      "damage=$damage (baseAtk=$baseAtk atkMod=$atkMod hardDefMod=$hardDefMod critMod=$critMod softDef=$softDef)"
    }

    return max(MIN_DAMAGE, damage.toInt())
  }

  /**
   * Attack power before the target is considered at all.
   *
   * The status term is doubled and the weapon term is not: an unarmed fighter has to be able to hurt something,
   * and with no equipment system every weapon term is zero. The two variance rolls are RO's - a wide one on the
   * attack itself so no two swings are alike, and a narrow one on the weapon so a good weapon stays reliably
   * good.
   */
  private fun getBaseAttack(battleCtx: EntityBattleContext): Float {
    val statusAtk = getStatusAttack(battleCtx)
    val weaponAtk = calculateWeaponAtk(battleCtx)
    val ammoAtk = getAmmoAttack(battleCtx)
    val bonusAtk = getBonusAttack(battleCtx)

    var baseAtk = STATUS_ATTACK_WEIGHT * statusAtk * varMod() +
        weaponAtk * varMod(WEAPON_VARIANCE) +
        ammoAtk +
        bonusAtk

    baseAtk *= getElementMod(battleCtx)

    // A swing that computed its way to nothing still connected, and the floor here is what keeps the flat
    // soft-defence subtraction below from being the only thing that decides the outcome.
    return max(1f, baseAtk)
  }

  /**
   * How the attack's element fares against what the target is made of.
   *
   * One lookup, not two: the sketch this replaces multiplied by the same table twice, once as a "mod" and once
   * as a "bonus". An elemental *bonus* is an equipment and card concept in RO and belongs with those when they
   * exist, not as a second application of the same table.
   */
  private fun getElementMod(battleCtx: EntityBattleContext): Float {
    return ElementModifier.getModifierFloat(
      battleCtx.usedAttack.attackElement,
      battleCtx.defender.assumedElement
    )
  }

  /** A random factor in `[1 - variance, 1]`, so damage varies downward from its ceiling. */
  private fun varMod(variance: Float = ATTACK_VARIANCE): Float = 1 - random.nextFloat() * variance

  /**
   * Attack from the weapon itself, refinement included. Zero until something can be equipped.
   *
   * Refinement is linear rather than the quadratic over-refine curve RO uses: without weapon levels there is
   * nothing to scale the per-refine step by, and inventing a curve for a term that is always zero today would
   * be a balance decision made blind.
   */
  protected open fun calculateWeaponAtk(battleCtx: EntityBattleContext): Float {
    val weapon = battleCtx.weapon

    return weapon.atk + weapon.upgradeLevel * REFINE_ATTACK_PER_LEVEL
  }

  /** Attack derived purely from the attacker's own attributes - the term that carries an unarmed fighter. */
  protected abstract fun getStatusAttack(battleCtx: EntityBattleContext): Float

  /** Flat additions from scripts and status effects, per attack type. */
  protected abstract fun getBonusAttack(battleCtx: EntityBattleContext): Float

  protected abstract fun getAmmoAttack(battleCtx: EntityBattleContext): Float

  /** The flat, attribute-derived defence subtracted after every multiplier. */
  protected abstract fun getSoftDefense(battleCtx: EntityBattleContext): Float

  /** The percentage defence multiplier: below 1 it shields, above 1 it exposes. See [physicalDefenseModifier]. */
  protected abstract fun getHardDefenseModifier(battleCtx: EntityBattleContext): Float

  protected abstract fun getAttackModifier(battleCtx: EntityBattleContext): Float

  /**
   * Shared by both calculators: there is no equipment DEF to reduce damage by, so the only thing that can move
   * this is a script or status effect having set [DamageVariables.physicalDefenseMod] away from 1.
   *
   * Above 1 it shields (a mod of 2 halves the damage), below 1 it exposes (a mod of 0.5 doubles it), which is
   * what a defence-piercing effect would set. Bounded at both ends: no stack of shields makes an entity immune,
   * and no amount of piercing turns a swing into a one-shot.
   */
  protected fun physicalDefenseModifier(battleCtx: EntityBattleContext): Float =
    defenseModifier(battleCtx.damageVariables.physicalDefenseMod)

  /** The magic counterpart of [physicalDefenseModifier], off [DamageVariables.magicDefenseMod]. */
  protected fun magicDefenseModifier(battleCtx: EntityBattleContext): Float =
    defenseModifier(battleCtx.damageVariables.magicDefenseMod)

  private fun defenseModifier(mod: Float): Float {
    if (mod <= 0f) {
      return MAX_HARD_DEFENSE_MULTIPLIER
    }

    return (1f / mod).clamp(MIN_HARD_DEFENSE_MULTIPLIER, MAX_HARD_DEFENSE_MULTIPLIER)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** A hit that landed always hurts, however armoured the target. */
    const val MIN_DAMAGE = 1

    /**
     * Doubles the attribute-derived term. Without it an unarmed level-10 fighter (ATK 17) would be fully
     * absorbed by an equally-levelled target's soft defence (16) and every swing would land for the minimum.
     */
    private const val STATUS_ATTACK_WEIGHT = 2f

    /** RO's wide roll on the attack itself: a swing lands for 85%-100% of its ceiling. */
    private const val ATTACK_VARIANCE = 0.15f

    /** The narrow roll on the weapon term, so gear stays predictable in a way attributes do not. */
    private const val WEAPON_VARIANCE = 0.05f

    /** Pre-renewal RO's critical bonus: +40%, on top of ignoring defence. */
    private const val CRITICAL_DAMAGE_BONUS = 1.4f

    /** Even a fully shielded target takes 5% through, so nothing can become unkillable. */
    private const val MIN_HARD_DEFENSE_MULTIPLIER = 0.05f

    /**
     * The other end: a defence modifier driven to zero quadruples damage and stops there. Without a ceiling a
     * script that set the mod to a small fraction - or to zero, and divided - would one-shot anything.
     */
    private const val MAX_HARD_DEFENSE_MULTIPLIER = 4f

    private const val REFINE_ATTACK_PER_LEVEL = 2f
  }
}
