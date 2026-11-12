package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.ElementModifier
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillLevelCost
import net.bestia.zone.skill.persistence.Skill

/**
 * The Wizard's four bolts: `MATK * (100% + 20%/lv)` of the row's element, weighed by element and resistance, less
 * MDEF. Each level casts 0.2 s faster. A bestia's bolts are `BestiaMagicSkill`.
 */
abstract class MasterBolt(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun costOf(skill: Skill, level: Int): SkillLevelCost {
    val castTime = skill.castTime - CAST_SECONDS_SAVED_PER_LEVEL * level.coerceIn(1, MAX_LEVEL)

    return SkillLevelCost.of(skill).copy(castTimeSeconds = castTime)
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null

    return HitDamage(damageOf(battle, ctx.skillLevel))
  }

  companion object {

    fun damageOf(battle: EntityBattleContext, level: Int): Int {
      val element = battle.usedAttack.attackElement
      val matk = battle.attacker.derivedStatusValues.matk + battle.weapon.matk
      val raw = matk * (100 + MATK_PERCENT_PER_LEVEL * level.coerceIn(1, MAX_LEVEL)) / 100f
      val resisted = (battle.defender.elementResistance[element.base] ?: 0).coerceIn(0, 100)
      val weighed = raw * ElementModifier.getModifierFloat(element, battle.defender.assumedElement) * (100 - resisted) / 100f

      return (weighed - battle.defender.defense.magicDefense).toInt().coerceAtLeast(MIN_DAMAGE)
    }

    private const val MAX_LEVEL = 5
    private const val MATK_PERCENT_PER_LEVEL = 20
    private const val CAST_SECONDS_SAVED_PER_LEVEL = 0.2f
    private const val MIN_DAMAGE = 1
  }
}
