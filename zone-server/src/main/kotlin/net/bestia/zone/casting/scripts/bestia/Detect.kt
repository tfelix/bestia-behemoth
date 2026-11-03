package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * Senses masters and player bestia around the caster, hidden ones too, and marks them on its player's map for a
 * while. Nothing is revealed in the world: the counter to hiding is knowing where to look.
 */
@Component
class Detect : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.detect(SECONDS)

    return null
  }

  private companion object {
    const val SECONDS = 30f
  }
}
