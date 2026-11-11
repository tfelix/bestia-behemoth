package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * Street Stall (`skills.yml` id 178): puts a stall down at the Trader's feet for an hour, with
 * 3 listing places and 2 more per level. See `net.bestia.zone.trade.stall.StreetStalls`.
 */
@Component
class StreetStall : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean = true

  override fun execute(ctx: SkillContext): Damage? {
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    ctx.world.pitchStall(maxListings = BASE_LISTINGS + LISTINGS_PER_LEVEL * (level - 1), seconds = OPEN_SECONDS)

    return null
  }

  private companion object {
    const val MAX_LEVEL = 5
    const val BASE_LISTINGS = 3
    const val LISTINGS_PER_LEVEL = 2
    const val OPEN_SECONDS = 3600
  }
}
