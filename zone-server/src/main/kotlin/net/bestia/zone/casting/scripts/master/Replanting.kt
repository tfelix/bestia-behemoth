package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * Replanting (`skills.yml` id 176): plants a sapling in a tree stump for a log - the log is the skill's reagent. The
 * tree grows back `-25%/lv` faster than one left to nature, and at Lv.3 gives one more log when it is felled grown.
 */
@Component
class Replanting : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean = ctx.targetPosition != null

  override fun execute(ctx: SkillContext): Damage? {
    val at = ctx.targetPosition ?: return null
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    ctx.world.replant(at, growthPercent = 100 - FASTER_PERCENT_PER_LEVEL * level, bonusYield = if (level >= MAX_LEVEL) 1 else 0)

    return null
  }

  private companion object {
    const val FASTER_PERCENT_PER_LEVEL = 25
    const val MAX_LEVEL = 3
  }
}
