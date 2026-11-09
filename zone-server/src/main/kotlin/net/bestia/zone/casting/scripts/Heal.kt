package net.bestia.zone.casting.scripts

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/** Heal (`skills.yml` id 4), with the Ragnarok Online formula: `[(BaseLv + INT) / 8] * (4 + 8 * SkillLv)`. */
@Component
class Heal(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null

    val levelAndInt = battle.attacker.level + battle.attacker.statusValues.intelligence
    val roHeal = (levelAndInt / 8) * (4 + 8 * battle.usedAttack.level)

    return Heal((roHeal * battle.damageVariables.healMod).toInt())
  }
}
