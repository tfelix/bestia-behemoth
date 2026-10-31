package net.bestia.zone.casting.scripts.utility

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/**
 * Sets the grass alight where it is aimed and does nothing else - no patch, no damage. The ground fire is the
 * effect: it spreads and scars as any fire does, and on stone or in the rain it simply does not catch.
 */
@Component
class Kindle(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun execute(ctx: SkillContext): Damage? {
    val radius = ctx.battle.usedAttack.aoeRadius?.roundToLong() ?: SINGLE_TILE
    ctx.world.igniteGroundFire(centre = ctx.aimedAt, radiusTiles = radius)

    return null
  }

  private companion object {
    const val SINGLE_TILE = 0L
  }
}
