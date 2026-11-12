package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.effects.LineFootprint
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Facing
import org.springframework.stereotype.Component

/**
 * Fire Wall (`skills.yml` id 107): three tiles of flame across the line of the cast for 20 s. Twice a second each
 * tile burns the foe on it for `MATK * (110% + 20%/lv after Lv.1)` and throws it `1 m/lv` back across the wall.
 *
 * One area per tile rather than one with a [LineFootprint]: the client draws an effect by its id alone, so it
 * could not turn a single wall to face the caster.
 */
@Component
class FireWall(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is GroundBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val ground = ctx.requireGroundContext()
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)
    val wall = LineFootprint.across(ground.attacker.position, ground.targetPosition, LENGTH_TILES)
    val facesAwayFromCaster = Facing().apply { turnTowards(ground.attacker.position, ground.targetPosition) }
    val across = Vec3L(facesAwayFromCaster.dx.toLong(), facesAwayFromCaster.dy.toLong(), 0)

    wall.tiles().forEach { tile ->
      ctx.world.spawnAreaEffect(centre = tile, visualId = VISUAL_ID, effect = flames(ctx, ground, level, across))
    }

    return null
  }

  private fun flames(ctx: SkillContext, ground: GroundBattleContext, level: Int, across: Vec3L): AreaEffect {
    return AreaEffect.lasting(
      casterId = ctx.casterId,
      skillId = ctx.skillId,
      skillLevel = level,
      radiusTiles = ONE_TILE,
      damagePerTick = WizardArea.damageOf(ground, WizardArea.bonusPercentOf(level)),
      tickIntervalSeconds = TICK_SECONDS,
      durationSeconds = DURATION_SECONDS,
      element = ground.usedAttack.attackElement,
      mechanics = WizardArea.SPELL.copy(knockbackTiles = level, knockbackAcross = across),
    )
  }

  private companion object {
    const val MAX_LEVEL = 3
    const val LENGTH_TILES = 3
    const val ONE_TILE = 0L
    const val DURATION_SECONDS = 20f

    /** Often enough that a foe walking through cannot cross a tile between two ticks. */
    const val TICK_SECONDS = 0.5f

    /** `7_fire_wall.tres` in the client's `EffectVisual/DB`. */
    const val VISUAL_ID = 7L
  }
}
