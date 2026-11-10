package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * Base Camp (`skills.yml` id 181): turns a burning campfire - the caster's or a party member's - into a camp for
 * 30/60/90 minutes. See [net.bestia.zone.casting.camp.BaseCamps] for what the camp does.
 */
@Component
class BaseCamp : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean = ctx.targetPosition != null

  override fun execute(ctx: SkillContext): Damage? {
    val at = ctx.targetPosition ?: return null
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    ctx.world.pitchCamp(at, level, durationSeconds = MINUTES_PER_LEVEL * 60L * level)

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
    const val MINUTES_PER_LEVEL = 30
  }
}
