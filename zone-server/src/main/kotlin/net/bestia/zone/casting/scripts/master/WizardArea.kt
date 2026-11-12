package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.ecs.effects.AreaMechanics

/**
 * What the Wizard's area spells share: each tick or strike is the caster's MATK raised by the level's bonus, and
 * lands on foes as an `ElementalSpell` of the row's element.
 */
object WizardArea {

  /** The mechanics every damaging Wizard area starts from. */
  val SPELL = AreaMechanics(landsAsElementalSpell = true)

  fun damageOf(battle: BattleContext, bonusPercent: Int): Int {
    val matk = battle.attacker.derivedStatusValues.matk + battle.weapon.matk

    return (matk * (100 + bonusPercent) / 100).coerceAtLeast(MIN_DAMAGE)
  }

  /** `+10%` at Lv.1 and `+20%` more per level, the table most of them share. */
  fun bonusPercentOf(level: Int): Int {
    return FIRST_LEVEL_BONUS + BONUS_PER_LEVEL * (level - 1)
  }

  private const val FIRST_LEVEL_BONUS = 10
  private const val BONUS_PER_LEVEL = 20
  private const val MIN_DAMAGE = 1
}
