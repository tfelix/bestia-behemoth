package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * Echo Sounding (`skills.yml` id 183): marks the hollows within 10 m, 5 m more per level, and from level 3 how deep
 * and how large each one is. See [net.bestia.zone.casting.sense.HollowFinder].
 */
@Component
class EchoSounding : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean = true

  override fun execute(ctx: SkillContext): Damage? {
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    ctx.world.soundForHollows(
      radiusMetres = BASE_RADIUS_METRES + RADIUS_METRES_PER_LEVEL * (level - 1),
      revealsDepth = level >= DEPTH_LEVEL,
    )

    return null
  }

  private companion object {
    const val MAX_LEVEL = 5
    const val BASE_RADIUS_METRES = 10
    const val RADIUS_METRES_PER_LEVEL = 5
    const val DEPTH_LEVEL = 3
  }
}
