package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * Improve Concentration (`skills.yml` id 158): puts [StatusEffectId.IMPROVE_CONCENTRATION] on the Hunter, and the
 * cast ends every Hide and Cloak within 3 m once.
 */
@Component
class ImproveConcentration : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.revealHiddenNear(REVEAL_RADIUS_TILES)
    ctx.world.applyStatusEffect(
      ctx.casterId, StatusEffectId.IMPROVE_CONCENTRATION.id, level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)
    )

    return null
  }

  companion object {
    private const val MAX_LEVEL = 5
    const val REVEAL_RADIUS_TILES = 3L
  }
}
