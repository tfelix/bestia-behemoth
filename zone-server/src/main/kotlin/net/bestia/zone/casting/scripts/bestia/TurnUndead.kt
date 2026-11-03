package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.TrueDamage
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component
import java.util.Random

/**
 * Only for the undead: a chance of [BASE_KILL_PERCENT] plus [KILL_PERCENT_PER_LEVEL] per skill level to destroy
 * the target outright, else a holy bolt. A boss is never destroyed outright.
 */
@Component
class TurnUndead(
  losService: LineOfSightService,
  private val random: Random = ThreadLocalRandomSource,
) : BestiaMagicSkill(losService, random) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val target = ctx.targetEntityId ?: return false

    return Undeath.isUndead(ctx.world.natureOf(target)) && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null
    val nature = ctx.targetEntityId?.let { ctx.world.natureOf(it) }
    val chance = BASE_KILL_PERCENT + KILL_PERCENT_PER_LEVEL * ctx.skillLevel

    if (nature?.boss == false && random.nextInt(100) < chance) {
      return TrueDamage(battle.defender.maxHealth)
    }

    return super.execute(ctx)
  }

  private companion object {
    const val BASE_KILL_PERCENT = 8
    const val KILL_PERCENT_PER_LEVEL = 1
  }
}
