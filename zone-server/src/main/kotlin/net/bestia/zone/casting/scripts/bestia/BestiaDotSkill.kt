package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/**
 * The dot archetype: Ember for any element. A patch that hurts everything standing in it,
 * `(lv/8 + INT/2 + MATK/4) * skillLv * coef` per tick. Only a fire patch sets the grass alight.
 */
@Component
class BestiaDotSkill(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun execute(ctx: SkillContext): Damage? {
    val caster = ctx.battle.attacker
    val element = ctx.battle.usedAttack.attackElement
    val radius = ctx.battle.usedAttack.aoeRadius?.roundToLong() ?: DEFAULT_RADIUS_TILES

    val base = caster.level / 8 + caster.statusValues.intelligence / 2
    val matk = caster.derivedStatusValues.matk + ctx.battle.weapon.matk
    val perTick = ((base + matk / 4) * ctx.battle.usedAttack.level * ctx.attackCoefficient).toInt()

    ctx.world.spawnAreaEffect(
      centre = ctx.aimedAt,
      visualId = BestiaPatchVisual.idOf(element),
      effect = AreaEffect.lasting(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = ctx.skillLevel,
        radiusTiles = radius,
        damagePerTick = perTick.coerceAtLeast(MIN_DAMAGE_PER_TICK),
        tickIntervalSeconds = TICK_INTERVAL_SECONDS,
        durationSeconds = DURATION_SECONDS,
        element = element
      )
    )

    if (element == Element.FIRE) {
      ctx.world.igniteGroundFire(centre = ctx.aimedAt, radiusTiles = radius)
    }

    return null
  }

  private companion object {
    // Ember's cadence, which the docs calculator assumes for every dot.
    const val TICK_INTERVAL_SECONDS = 1.2f
    const val DURATION_SECONDS = 9.6f
    const val MIN_DAMAGE_PER_TICK = 1
    const val DEFAULT_RADIUS_TILES = 1L
  }
}
