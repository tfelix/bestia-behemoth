package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.attack.AttackStrategyFactory
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * The strike, power strike and shot archetypes: a default attack whose damage is scaled by the skill's
 * coefficient. It misses and crits like a swing, because the docs fit it against one.
 */
@Component
class BestiaPhysicalSkill(
  private val attackStrategies: AttackStrategyFactory,
) : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val battle = ctx.battle as? EntityBattleContext ?: return false

    return attackStrategies.getAttackStrategy(battle).isAttackPossible(battle)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null
    battle.damageVariables.attackPhysicalMod = ctx.attackCoefficient

    return attackStrategies.getAttackStrategy(battle).execute(battle)
  }
}
