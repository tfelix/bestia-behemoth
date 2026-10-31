package net.bestia.zone.casting.scripts.utility

import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component
import java.util.Random

/**
 * A bestia forages, sheds or digs: each of its row's `yields` lands at its feet by its own chance. On the ground
 * rather than in a bag, so nothing about who carries it or how much can go wrong, and the master picks it up
 * like loot. The row's cooldown keeps it from being a farm.
 */
@Component
class Gather(
  private val random: Random = ThreadLocalRandomSource,
) : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    val at = ctx.world.positionOf(ctx.casterId) ?: return null

    ctx.skill.yields
      .filter { random.nextInt(CERTAIN) < it.chance }
      .forEach { ctx.world.dropItem(it.itemId, amount = 1, at = at) }

    return null
  }

  private companion object {
    const val CERTAIN = 10_000
  }
}
