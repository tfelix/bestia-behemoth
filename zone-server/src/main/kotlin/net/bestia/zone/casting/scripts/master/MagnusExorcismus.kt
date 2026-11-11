package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.effects.AreaMechanics
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/**
 * Magnus Exorcismus (`skills.yml` id 93): a holy wave of `MATK * (100% + 15%/lv)` every 3 s for 15 s on every foe
 * in the area, weighed by element. Demons and the Undead take `10%/lv` more.
 */
@Component
class MagnusExorcismus(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is GroundBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val ground = ctx.requireGroundContext()
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)
    val matk = ground.attacker.derivedStatusValues.matk + ground.weapon.matk

    ctx.world.spawnAreaEffect(
      centre = ground.targetPosition,
      visualId = VISUAL_ID,
      effect = AreaEffect.lasting(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = level,
        radiusTiles = ground.usedAttack.aoeRadius?.roundToLong() ?: DEFAULT_RADIUS_TILES,
        damagePerTick = (matk * (100 + MATK_PERCENT_PER_LEVEL * level) / 100).coerceAtLeast(MIN_DAMAGE),
        tickIntervalSeconds = WAVE_SECONDS,
        durationSeconds = DURATION_SECONDS,
        element = Element.HOLY,
        mechanics = AreaMechanics(weighsElement = true, unholyBonusPercent = UNHOLY_PERCENT_PER_LEVEL * level),
      ),
    )

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
    const val MATK_PERCENT_PER_LEVEL = 15
    const val UNHOLY_PERCENT_PER_LEVEL = 10
    const val WAVE_SECONDS = 3f
    const val DURATION_SECONDS = 15f
    const val DEFAULT_RADIUS_TILES = 3L
    const val MIN_DAMAGE = 1

    /** `5_magnus_exorcismus.tres` in the client's `EffectVisual/DB`. */
    const val VISUAL_ID = 5L
  }
}
