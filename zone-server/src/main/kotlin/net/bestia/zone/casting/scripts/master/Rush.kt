package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Rush (`skills.yml` id 149): the Brawler dashes up to 6 m next to a foe and lands one basic blow on it. Ground it
 * cannot step onto stops the dash short; then nothing is struck.
 */
@Component
class Rush(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val target = ctx.targetEntityId ?: return false

    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx) && ctx.world.isFoe(target)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    if (!Charge.dashNextTo(ctx, target)) return null

    ctx.world.strike(target, ctx.battle.usedAttack, BASIC_BLOW_PERCENT)

    return null
  }

  companion object {
    const val BASIC_BLOW_PERCENT = 100
  }
}
