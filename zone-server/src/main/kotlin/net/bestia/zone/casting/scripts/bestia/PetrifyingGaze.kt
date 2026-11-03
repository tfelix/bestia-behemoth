package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component
import kotlin.math.sqrt

/**
 * Stiffens every foe in a cone in front of the caster. A foe already stiff from a gaze before turns to stone for a
 * moment instead: a short stun.
 */
@Component
class PetrifyingGaze(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun execute(ctx: SkillContext): Damage? {
    val from = ctx.world.positionOf(ctx.casterId) ?: return null
    val towards = ctx.aimedAt

    ctx.world.entitiesInCube(from, CONE_TILES * 2)
      .filter { ctx.world.isFoe(it) }
      .filter { id -> ctx.world.positionOf(id)?.let { isInCone(from, towards, it) } == true }
      .forEach { id ->
        if (ctx.world.hasEffect(id, StatusEffectId.STIFFENED.id)) {
          ctx.world.inflict(id, StatusEffectId.STUN.name, chancePercent = 100, STUN_SECONDS, ctx.skillLevel)
        } else {
          ctx.world.inflict(id, StatusEffectId.STIFFENED.name, chancePercent = 100, durationSeconds = null, ctx.skillLevel)
        }
      }

    return null
  }

  /** Within [CONE_TILES] and no more than 45 degrees off the line from [from] to [towards]. */
  private fun isInCone(from: Vec3L, towards: Vec3L, at: Vec3L): Boolean {
    val ax = (towards.x - from.x).toDouble()
    val ay = (towards.y - from.y).toDouble()
    val bx = (at.x - from.x).toDouble()
    val by = (at.y - from.y).toDouble()
    val lengths = sqrt(ax * ax + ay * ay) * sqrt(bx * bx + by * by)
    if (lengths < 1e-6) return false

    return (ax * bx + ay * by) / lengths >= COS_HALF_ANGLE && bx * bx + by * by <= CONE_TILES * CONE_TILES
  }

  private companion object {
    const val CONE_TILES = 4L
    const val COS_HALF_ANGLE = 0.7071
    const val STUN_SECONDS = 2.0
  }
}
