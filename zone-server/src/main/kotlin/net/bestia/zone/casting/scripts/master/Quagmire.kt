package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.effects.AreaMechanics
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/**
 * Quagmire (`skills.yml` id 112): mud over the 5x5 aimed at for 30 s. Every foe in it is [StatusEffectId.QUAGMIRE]
 * at the cast's level. A bestia's mire is `BestiaZoneSkill`.
 */
@Component
class Quagmire(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is GroundBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val ground = ctx.requireGroundContext()
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    ctx.world.spawnAreaEffect(
      centre = ground.targetPosition,
      visualId = VISUAL_ID,
      effect = AreaEffect.lasting(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = level,
        radiusTiles = ground.usedAttack.aoeRadius?.roundToLong() ?: DEFAULT_RADIUS_TILES,
        damagePerTick = 0,
        tickIntervalSeconds = TICK_SECONDS,
        durationSeconds = DURATION_SECONDS,
        mechanics = AreaMechanics(zoneStatus = StatusEffectId.QUAGMIRE),
      ),
    )

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
    const val DURATION_SECONDS = 30f
    const val DEFAULT_RADIUS_TILES = 2L

    /** Shorter than the status lasts, so it never lapses in the mud and ends soon after wading out. */
    const val TICK_SECONDS = 0.5f

    /** `11_quagmire.tres` in the client's `EffectVisual/DB`. */
    const val VISUAL_ID = 11L
  }
}
