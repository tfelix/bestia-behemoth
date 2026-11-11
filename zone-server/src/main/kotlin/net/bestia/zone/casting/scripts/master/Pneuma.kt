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
 * Pneuma (`skills.yml` id 84): still air over the ground for 20 s. Whoever stands in it, foe or friend, is
 * [StatusEffectId.SHELTERED], so every arrow, bolt and throw at them misses.
 */
@Component
class Pneuma(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is GroundBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val ground = ctx.requireGroundContext()

    ctx.world.spawnAreaEffect(
      centre = ground.targetPosition,
      visualId = VISUAL_ID,
      effect = AreaEffect.lasting(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = ctx.skillLevel,
        radiusTiles = ground.usedAttack.aoeRadius?.roundToLong() ?: DEFAULT_RADIUS_TILES,
        damagePerTick = 0,
        tickIntervalSeconds = TICK_SECONDS,
        durationSeconds = DURATION_SECONDS,
        mechanics = AreaMechanics(zoneStatus = StatusEffectId.SHELTERED, zoneStatusForEveryone = true),
      ),
    )

    return null
  }

  private companion object {
    const val DURATION_SECONDS = 20f
    const val DEFAULT_RADIUS_TILES = 1L

    /** Shorter than Sheltered lasts, so it never lapses inside and ends soon after walking out. */
    const val TICK_SECONDS = 0.5f

    /** `3_pneuma.tres` in the client's `EffectVisual/DB`. */
    const val VISUAL_ID = 3L
  }
}
