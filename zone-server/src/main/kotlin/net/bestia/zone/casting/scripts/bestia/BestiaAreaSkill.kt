package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/**
 * The aoe archetype: one burst of spell damage on everything around the aimed-at point. It lands as an area
 * effect with a single tick, so the damage gate and the kill credit work as for Ember. Like Ember's patch it
 * is computed without a defender, so no magic defence or element weighs on it.
 */
@Component
class BestiaAreaSkill(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun execute(ctx: SkillContext): Damage? {
    val perTarget = (BestiaSpellPower.of(ctx) * ctx.attackCoefficient).toInt().coerceAtLeast(MIN_DAMAGE)

    ctx.world.spawnAreaEffect(
      centre = ctx.aimedAt,
      visualId = BestiaPatchVisual.idOf(ctx.battle.usedAttack.attackElement),
      effect = AreaEffect.lasting(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = ctx.skillLevel,
        radiusTiles = ctx.battle.usedAttack.aoeRadius?.roundToLong() ?: DEFAULT_RADIUS_TILES,
        damagePerTick = perTarget,
        tickIntervalSeconds = BURST_SECONDS,
        durationSeconds = BURST_SECONDS,
        element = ctx.battle.usedAttack.attackElement
      )
    )

    return null
  }

  private companion object {
    const val MIN_DAMAGE = 1
    const val DEFAULT_RADIUS_TILES = 1L
    const val BURST_SECONDS = 0.2f
  }
}
