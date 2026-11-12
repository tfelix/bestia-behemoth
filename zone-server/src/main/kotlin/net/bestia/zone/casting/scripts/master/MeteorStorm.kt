package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/**
 * Meteor Storm (`skills.yml` id 108): `3 + 2/lv after Lv.1` meteors fall at random spots of the 7x7 aimed at, spread
 * evenly over 10 s. Each burns every foe in the 7x7 around where it lands for `MATK * (110% + 20%/lv after Lv.1)`.
 */
@Component
class MeteorStorm(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is GroundBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val ground = ctx.requireGroundContext()
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)
    val meteors = FIRST_LEVEL_METEORS + METEORS_PER_LEVEL * (level - 1)

    ctx.world.spawnAreaEffect(
      centre = ground.targetPosition,
      visualId = VISUAL_ID,
      effect = AreaEffect(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = level,
        radiusTiles = ground.usedAttack.aoeRadius?.roundToLong() ?: DEFAULT_RADIUS_TILES,
        damagePerTick = WizardArea.damageOf(ground, WizardArea.bonusPercentOf(level)),
        tickIntervalSeconds = DURATION_SECONDS / meteors,
        remainingTicks = meteors,
        element = ground.usedAttack.attackElement,
        mechanics = WizardArea.SPELL.copy(strikeRadiusTiles = SPLASH_RADIUS_TILES),
      ),
    )

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
    const val FIRST_LEVEL_METEORS = 3
    const val METEORS_PER_LEVEL = 2
    const val DURATION_SECONDS = 10f
    const val DEFAULT_RADIUS_TILES = 3L
    const val SPLASH_RADIUS_TILES = 3

    /** `8_meteor_storm.tres` in the client's `EffectVisual/DB`. */
    const val VISUAL_ID = 8L
  }
}
