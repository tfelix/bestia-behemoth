package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.effects.AreaStatus
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/**
 * Storm Gust (`skills.yml` id 109): a freezing gale over the 9x9 aimed at for 10 s. Every second a wave hits each foe
 * in it for `MATK * (108% + 16%/lv after Lv.1)` and may freeze it, `5% + 10%/lv after Lv.1`.
 */
@Component
class StormGust(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is GroundBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val ground = ctx.requireGroundContext()
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)
    val freeze = AreaStatus(
      effect = StatusEffectId.FROZEN,
      chancePercent = FIRST_LEVEL_FREEZE_PERCENT + FREEZE_PERCENT_PER_LEVEL * (level - 1),
      durationSeconds = null,
      level = level,
    )

    ctx.world.spawnAreaEffect(
      centre = ground.targetPosition,
      visualId = VISUAL_ID,
      effect = AreaEffect.lasting(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = level,
        radiusTiles = ground.usedAttack.aoeRadius?.roundToLong() ?: DEFAULT_RADIUS_TILES,
        damagePerTick = WizardArea.damageOf(ground, FIRST_LEVEL_BONUS + BONUS_PER_LEVEL * (level - 1)),
        tickIntervalSeconds = WAVE_SECONDS,
        durationSeconds = DURATION_SECONDS,
        element = ground.usedAttack.attackElement,
        mechanics = WizardArea.SPELL.copy(statuses = listOf(freeze)),
      ),
    )

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
    const val FIRST_LEVEL_BONUS = 8
    const val BONUS_PER_LEVEL = 16
    const val FIRST_LEVEL_FREEZE_PERCENT = 5
    const val FREEZE_PERCENT_PER_LEVEL = 10
    const val WAVE_SECONDS = 1f
    const val DURATION_SECONDS = 10f
    const val DEFAULT_RADIUS_TILES = 4L

    /** `9_storm_gust.tres` in the client's `EffectVisual/DB`. */
    const val VISUAL_ID = 9L
  }
}
