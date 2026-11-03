package net.bestia.zone.casting.scripts.bestia

import java.util.Random
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/**
 * The aoe and ultimate-area archetypes: one burst of spell damage on everything around the aimed-at point. It
 * lands as an area effect with a single tick, so the damage gate and the kill credit work as for Ember. Like
 * Ember's patch it is computed without a defender, so no magic defence or element weighs on it.
 *
 * Channelled, it bursts again on every channel tick; with pillars each of those strikes one random tile of the
 * area instead of all of it.
 */
@Component
class BestiaAreaSkill(
  losService: LineOfSightService,
  private val random: Random = ThreadLocalRandomSource,
) : BasicMagicSkillStrategy(losService) {

  override fun execute(ctx: SkillContext): Damage? {
    val centre = BestiaSkillMechanics.centreOf(ctx)
    burst(ctx, centre, radiusOf(ctx), durationOf(ctx))

    val ignite = ctx.skill.mechanics.igniteChance
    if (ignite > 0 && random.nextInt(100) < ignite) {
      ctx.world.igniteGroundFire(centre, radiusTiles = 0)
    }

    return null
  }

  override fun onChannelTick(ctx: SkillContext, tick: Int) {
    val centre = BestiaSkillMechanics.centreOf(ctx)
    val radius = radiusOf(ctx)

    if (ctx.skill.mechanics.pillars) {
      val pillar = Vec3L(
        centre.x + random.nextLong(-radius, radius + 1),
        centre.y + random.nextLong(-radius, radius + 1),
        centre.z
      )
      burst(ctx, pillar, radiusTiles = 0, BURST_SECONDS)
    } else {
      burst(ctx, centre, radius, BURST_SECONDS)
    }
  }

  private fun burst(ctx: SkillContext, centre: Vec3L, radiusTiles: Long, seconds: Float) {
    val perTarget = (BestiaSpellPower.of(ctx) * ctx.attackCoefficient).toInt().coerceAtLeast(MIN_DAMAGE)

    ctx.world.spawnAreaEffect(
      centre = centre,
      visualId = BestiaPatchVisual.idOf(ctx.battle.usedAttack.attackElement),
      effect = AreaEffect.lasting(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = ctx.skillLevel,
        radiusTiles = radiusTiles,
        damagePerTick = perTarget,
        tickIntervalSeconds = minOf(seconds, TICK_SECONDS),
        durationSeconds = seconds,
        element = ctx.battle.usedAttack.attackElement,
        mechanics = BestiaSkillMechanics.areaMechanics(ctx, centre)
      )
    )
  }

  private fun radiusOf(ctx: SkillContext): Long {
    return ctx.battle.usedAttack.aoeRadius?.roundToLong() ?: DEFAULT_RADIUS_TILES
  }

  /** A burst lands once; an area that lingers, like Endless Night or a drifting storm, hits every second. */
  private fun durationOf(ctx: SkillContext): Float {
    val seconds = ctx.skill.mechanics.areaSeconds

    return if (seconds > 0f) seconds else BURST_SECONDS
  }

  private companion object {
    const val MIN_DAMAGE = 1
    const val DEFAULT_RADIUS_TILES = 1L
    const val BURST_SECONDS = 0.2f
    const val TICK_SECONDS = 1f
  }
}
