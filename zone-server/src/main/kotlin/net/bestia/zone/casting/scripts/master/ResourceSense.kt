package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * Resource Sense (`skills.yml` id 48): points at the nearest ore, rare herbs, timber and game within 1 km per level,
 * with +10% accuracy per level. See [net.bestia.zone.casting.sense.ResourceSensing].
 */
@Component
class ResourceSense : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean = true

  override fun execute(ctx: SkillContext): Damage? {
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    ctx.world.senseResources(rangeMetres = METRES_PER_LEVEL * level, accuracy = ACCURACY_PER_LEVEL * level)

    return null
  }

  private companion object {
    const val MAX_LEVEL = 5
    const val METRES_PER_LEVEL = 1000.0
    const val ACCURACY_PER_LEVEL = 0.1
  }
}
