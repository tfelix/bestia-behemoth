package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.skill.BasicMagicSkillStrategy
import net.bestia.zone.battle.skill.SkillContext
import org.springframework.stereotype.Component

/** Heal (`skills.yml` id 4). */
@Component
class Heal(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null

    val effectFac = battle.damageVariables.healMod + battle.damageVariables.healMod
    val baseAmount =
      ((battle.attacker.level + battle.attacker.statusValues.intelligence) / 5) * battle.usedAttack.level * 3
    val matk =
      battle.weapon.upgradeLevel * battle.weapon.upgradeLevel + battle.weapon.matk + battle.attacker.derivedStatusValues.matk

    return Heal(((baseAmount * effectFac) / 100 + matk).toInt())
  }
}
