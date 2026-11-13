package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * Blasting (`skills.yml` id 179): a charge on the ground that goes off 3 s later. It breaks the rock `1 m/lv + 1 m`
 * around it and scatters the ore (see `Blasts`), and hits every foe in that radius for `ATK * (100% + 20%/lv)`. The
 * Blasting Charge it burns is the skill's reagent.
 */
@Component
class Blasting : SkillStrategy {

  /** Refused before the charge is spent, so ore too hard for the caster's Mining stays in the rock. */
  override fun isCastPossible(ctx: SkillContext): Boolean {
    val ground = ctx.battle as? GroundBattleContext ?: return false
    val refusal = ctx.world.blastRefusal(ground.targetPosition, radiusOf(ctx.skillLevel)) ?: return true

    ctx.world.tellCaster(refusal)
    return false
  }

  override fun execute(ctx: SkillContext): Damage? {
    val ground = ctx.requireGroundContext()
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)
    val radius = radiusOf(level)

    ctx.world.spawnAreaEffect(
      centre = ground.targetPosition,
      visualId = VISUAL_ID,
      effect = AreaEffect(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = level,
        radiusTiles = radius.toLong(),
        damagePerTick = damageOf(ground, level),
        tickIntervalSeconds = FUSE_SECONDS,
        remainingTicks = 1,
      ),
    )
    ctx.world.setBlastCharge(ground.targetPosition, radius)

    return null
  }

  companion object {
    private const val MAX_LEVEL = 5
    private const val FIRST_LEVEL_RADIUS = 2
    private const val BONUS_PERCENT_PER_LEVEL = 20
    private const val MIN_DAMAGE = 1

    /** As long as `Blasts` burns a fuse, so the foes are hit as the rock breaks. */
    private const val FUSE_SECONDS = 3f

    /** `30_blasting_charge.tres` in the client's `EffectVisual/DB`. */
    const val VISUAL_ID = 30L

    /** `2 m` at Lv.1: the client cannot draw a narrower hole. */
    fun radiusOf(level: Int): Int = FIRST_LEVEL_RADIUS + level.coerceIn(1, MAX_LEVEL) - 1

    fun damageOf(battle: BattleContext, level: Int): Int {
      val atk = battle.attacker.derivedStatusValues.atk + battle.weapon.atk

      return (atk * (100 + BONUS_PERCENT_PER_LEVEL * level) / 100).coerceAtLeast(MIN_DAMAGE)
    }
  }
}
