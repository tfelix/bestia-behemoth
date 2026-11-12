package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Sonic Blow (`skills.yml` id 130): eight weapon blows that share `340% + 40%/lv` ATK evenly. A dual wielder strikes
 * every second blow with the left hand; anyone else strikes all eight with the main hand.
 */
@Component
class SonicBlow(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    val isDualWielding = ctx.world.isDualWielding(ctx.casterId)

    hitPercents(ctx.skillLevel).forEachIndexed { hit, percent ->
      val offHand = isDualWielding && hit % 2 == 1
      ctx.world.strike(target, ctx.battle.usedAttack, percent, offHand)
    }

    return null
  }

  companion object {
    private const val MAX_LEVEL = 5
    const val HITS = 8

    fun atkPercent(level: Int): Int {
      return 340 + 40 * (level.coerceIn(1, MAX_LEVEL) - 1)
    }

    /** [atkPercent] split over the [HITS], rounded so the blows still add up to all of it. */
    fun hitPercents(level: Int): List<Int> {
      val total = atkPercent(level)

      return (0 until HITS).map { hit -> total * (hit + 1) / HITS - total * hit / HITS }
    }
  }
}
