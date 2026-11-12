package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * Earthquake (`skills.yml` id 114): the ground splits `2 m/lv` around where the caster stands for 8 s. Every second
 * it hits each foe on it for `MATK * (110% + 20%/lv after Lv.1)`. It stays where it was cast.
 */
@Component
class Earthquake : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    ctx.world.spawnAreaEffect(
      centre = WizardArea.casterCentre(ctx),
      // The client draws an area by its id alone, so each radius has its own visual.
      visualId = FIRST_VISUAL_ID + level - 1,
      effect = AreaEffect.lasting(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = level,
        radiusTiles = RADIUS_TILES_PER_LEVEL * level,
        damagePerTick = WizardArea.damageOf(ctx.battle, WizardArea.bonusPercentOf(level)),
        tickIntervalSeconds = SHOCK_SECONDS,
        durationSeconds = DURATION_SECONDS,
        element = ctx.battle.usedAttack.attackElement,
        mechanics = WizardArea.SPELL,
      ),
    )

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
    const val RADIUS_TILES_PER_LEVEL = 2L
    const val SHOCK_SECONDS = 1f
    const val DURATION_SECONDS = 8f

    /** `15_earthquake_1.tres` in the client's `EffectVisual/DB`; Lv.2 and Lv.3 follow it. */
    const val FIRST_VISUAL_ID = 15L
  }
}
