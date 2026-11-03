package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/**
 * Healing Circle and Sanctuary: a patch of ground that heals every friend inside, once for a circle and every
 * second for a sanctuary that lingers. Each tick heals what [BestiaHealSkill] would, split over the ticks.
 */
@Component
class BestiaAreaHealSkill(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun execute(ctx: SkillContext): Damage? {
    val centre = BestiaSkillMechanics.centreOf(ctx)
    val seconds = ctx.skill.mechanics.areaSeconds.takeIf { it > 0f } ?: BURST_SECONDS
    val ticks = (seconds / TICK_SECONDS).toInt().coerceAtLeast(1)

    ctx.world.spawnAreaEffect(
      centre = centre,
      visualId = BestiaPatchVisual.idOf(ctx.battle.usedAttack.attackElement),
      effect = AreaEffect.lasting(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = ctx.skillLevel,
        radiusTiles = ctx.battle.usedAttack.aoeRadius?.roundToLong() ?: DEFAULT_RADIUS_TILES,
        damagePerTick = 0,
        tickIntervalSeconds = minOf(seconds, TICK_SECONDS),
        durationSeconds = seconds,
        element = ctx.battle.usedAttack.attackElement,
        mechanics = BestiaSkillMechanics.areaMechanics(ctx, centre).copy(
          forAllies = true,
          healPerTick = (BestiaHealSkill.amountOf(ctx) / ticks).coerceAtLeast(1)
        )
      )
    )

    return null
  }

  private companion object {
    const val DEFAULT_RADIUS_TILES = 2L
    const val BURST_SECONDS = 0.2f
    const val TICK_SECONDS = 1f
  }
}
