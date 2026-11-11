package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BestiaKind
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
 * Sanctuary (`skills.yml` id 94): holy ground for 30 s. Every second it heals each ally inside `5%/lv` of their
 * max HP, and burns each Demon or Undead foe for the same share of the caster's max HP, weighed by element.
 */
@Component
class Sanctuary(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is GroundBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val ground = ctx.requireGroundContext()
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)
    val percent = PERCENT_PER_LEVEL * level

    ctx.world.spawnAreaEffect(
      centre = ground.targetPosition,
      visualId = VISUAL_ID,
      effect = AreaEffect.lasting(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = level,
        radiusTiles = ground.usedAttack.aoeRadius?.roundToLong() ?: DEFAULT_RADIUS_TILES,
        damagePerTick = (ground.attacker.maxHealth * percent / 100).coerceAtLeast(MIN_DAMAGE),
        tickIntervalSeconds = TICK_SECONDS,
        durationSeconds = DURATION_SECONDS,
        element = Element.HOLY,
        mechanics = AreaMechanics(
          forAllies = true,
          healPercentPerTick = percent,
          foeKinds = setOf(BestiaKind.DEMON, BestiaKind.UNDEAD),
          foeElements = setOf(Element.UNDEAD),
          weighsElement = true,
        ),
      ),
    )

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
    const val PERCENT_PER_LEVEL = 5
    const val TICK_SECONDS = 1f
    const val DURATION_SECONDS = 30f
    const val DEFAULT_RADIUS_TILES = 2L
    const val MIN_DAMAGE = 1

    /** `4_sanctuary.tres` in the client's `EffectVisual/DB`. */
    const val VISUAL_ID = 4L
  }
}
