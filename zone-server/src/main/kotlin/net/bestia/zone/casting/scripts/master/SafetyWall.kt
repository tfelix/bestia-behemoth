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

/**
 * Safety Wall (`skills.yml` id 103): one tile of solid mana for `20 s + 16 s/lv after Lv.1`. Every ally on it is
 * [StatusEffectId.SAFETY_WALL], which stops `2 + 4/lv after Lv.1` physical blows between them before the wall breaks.
 */
@Component
class SafetyWall(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

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
        radiusTiles = ONE_TILE,
        damagePerTick = 0,
        tickIntervalSeconds = TICK_SECONDS,
        durationSeconds = FIRST_LEVEL_SECONDS + SECONDS_PER_LEVEL * (level - 1),
        mechanics = AreaMechanics(
          zoneStatus = StatusEffectId.SAFETY_WALL,
          forAllies = true,
          absorbsHits = FIRST_LEVEL_HITS + HITS_PER_LEVEL * (level - 1),
        ),
      ),
    )

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
    const val ONE_TILE = 0L
    const val FIRST_LEVEL_SECONDS = 20f
    const val SECONDS_PER_LEVEL = 16f
    const val FIRST_LEVEL_HITS = 2
    const val HITS_PER_LEVEL = 4

    /** Shorter than the status lasts, so it never lapses on the tile and ends soon after stepping off. */
    const val TICK_SECONDS = 0.5f

    /** `6_safety_wall.tres` in the client's `EffectVisual/DB`. */
    const val VISUAL_ID = 6L
  }
}
