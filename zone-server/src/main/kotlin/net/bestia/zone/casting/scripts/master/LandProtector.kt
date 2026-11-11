package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.effects.AreaMechanics
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.casting.scripts.bestia.BestiaPatchVisual
import org.springframework.stereotype.Component

/**
 * Land Protector (`skills.yml` id 68): a zone of 3 m, 1 m more per level, for 15 s, 10 s more per level, in which no
 * ground spell takes root unless its Potency wins. See `GroundWards`.
 */
@Component
class LandProtector : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean = ctx.targetPosition != null

  override fun execute(ctx: SkillContext): Damage? {
    val at = ctx.targetPosition ?: return null
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    ctx.world.spawnAreaEffect(
      centre = at,
      visualId = BestiaPatchVisual.idOf(Element.HOLY),
      effect = AreaEffect.lasting(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = level,
        radiusTiles = BASE_RADIUS + (level - 1),
        damagePerTick = 0,
        tickIntervalSeconds = TICK_SECONDS,
        durationSeconds = BASE_SECONDS + SECONDS_PER_LEVEL * (level - 1),
        mechanics = AreaMechanics(blocksGroundSpells = true),
      ).copy(potency = ctx.potency),
    )

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
    const val BASE_RADIUS = 3L
    const val BASE_SECONDS = 15f
    const val SECONDS_PER_LEVEL = 10f
    const val TICK_SECONDS = 1f
  }
}
