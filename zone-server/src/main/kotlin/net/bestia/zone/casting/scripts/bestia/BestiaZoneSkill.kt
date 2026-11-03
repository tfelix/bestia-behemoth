package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/**
 * A ward or a mire laid on the ground: no damage, only the status its area keeps refreshing on whoever stands
 * inside, like Quagmire's on foes or Wind Ward's on friends. See [SkillRiders.areaRiders].
 */
@Component
class BestiaZoneSkill(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun execute(ctx: SkillContext): Damage? {
    val centre = SkillRiders.centreOf(ctx)
    val seconds = ctx.skill.mechanics.areaSeconds.takeIf { it > 0f } ?: DEFAULT_SECONDS

    ctx.world.spawnAreaEffect(
      centre = centre,
      visualId = BestiaPatchVisual.idOf(ctx.battle.usedAttack.attackElement),
      effect = AreaEffect.lasting(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = ctx.skillLevel,
        radiusTiles = ctx.battle.usedAttack.aoeRadius?.roundToLong() ?: DEFAULT_RADIUS_TILES,
        damagePerTick = 0,
        tickIntervalSeconds = TICK_SECONDS,
        durationSeconds = seconds,
        element = ctx.battle.usedAttack.attackElement,
        riders = SkillRiders.areaRiders(ctx, centre)
      )
    )

    return null
  }

  private companion object {
    const val DEFAULT_SECONDS = 10f
    const val DEFAULT_RADIUS_TILES = 2L
    /** Shorter than the zone statuses last, so walking out ends them within a second or two. */
    const val TICK_SECONDS = 0.5f
  }
}
