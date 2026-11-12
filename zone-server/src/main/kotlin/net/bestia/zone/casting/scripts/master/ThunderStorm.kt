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
 * Thunder Storm (`skills.yml` id 113): `3 + 2/lv after Lv.1` lightning bolts strike random spots of the 5x5 aimed at,
 * spread evenly over 8 s. Each hits every foe in the 3x3 around where it strikes for `MATK * (110% + 20%/lv after
 * Lv.1)`.
 */
@Component
class ThunderStorm(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is GroundBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val ground = ctx.requireGroundContext()
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)
    val strikes = FIRST_LEVEL_STRIKES + STRIKES_PER_LEVEL * (level - 1)

    ctx.world.spawnAreaEffect(
      centre = ground.targetPosition,
      visualId = VISUAL_ID,
      effect = AreaEffect(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = level,
        radiusTiles = ground.usedAttack.aoeRadius?.roundToLong() ?: DEFAULT_RADIUS_TILES,
        damagePerTick = WizardArea.damageOf(ground, WizardArea.bonusPercentOf(level)),
        tickIntervalSeconds = DURATION_SECONDS / strikes,
        remainingTicks = strikes,
        element = ground.usedAttack.attackElement,
        mechanics = WizardArea.SPELL.copy(strikeRadiusTiles = BOLT_RADIUS_TILES),
      ),
    )

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
    const val FIRST_LEVEL_STRIKES = 3
    const val STRIKES_PER_LEVEL = 2
    const val DURATION_SECONDS = 8f
    const val DEFAULT_RADIUS_TILES = 2L
    const val BOLT_RADIUS_TILES = 1

    /** `10_thunder_storm.tres` in the client's `EffectVisual/DB`. */
    const val VISUAL_ID = 10L
  }
}
