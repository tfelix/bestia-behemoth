package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.effects.AreaMechanics
import net.bestia.zone.battle.ecs.effects.AreaStatus
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * Frost Nova (`skills.yml` id 111): one burst of cold `2 m/lv` around the caster. It deals no damage; each foe in it
 * freezes at `15% + 20%/lv after Lv.1`.
 */
@Component
class FrostNova : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)
    val freeze = AreaStatus(
      effect = StatusEffectId.FROZEN,
      chancePercent = FIRST_LEVEL_FREEZE_PERCENT + FREEZE_PERCENT_PER_LEVEL * (level - 1),
      durationSeconds = null,
      level = level,
    )

    ctx.world.spawnAreaEffect(
      centre = WizardArea.casterCentre(ctx),
      // The client draws an area by its id alone, so each radius has its own visual.
      visualId = FIRST_VISUAL_ID + level - 1,
      effect = AreaEffect(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = level,
        radiusTiles = RADIUS_TILES_PER_LEVEL * level,
        damagePerTick = 0,
        tickIntervalSeconds = BURST_SECONDS,
        remainingTicks = 1,
        element = ctx.battle.usedAttack.attackElement,
        mechanics = AreaMechanics(statuses = listOf(freeze)),
      ),
    )

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
    const val RADIUS_TILES_PER_LEVEL = 2L
    const val FIRST_LEVEL_FREEZE_PERCENT = 15
    const val FREEZE_PERCENT_PER_LEVEL = 20

    /** Long enough for the client to show the burst before the area is gone. */
    const val BURST_SECONDS = 0.5f

    /** `12_frost_nova_1.tres` in the client's `EffectVisual/DB`; Lv.2 and Lv.3 follow it. */
    const val FIRST_VISUAL_ID = 12L
  }
}
