package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Dispel (`skills.yml` id 64), the Sage's: buffs and curses alike, `(50 + 10/lv)%` each, cut by a stronger spell. The
 * Yellow Mana Crystal is the skill's reagent. A bestia's Dispel is `scripts.bestia.Dispel`.
 */
@Component
class MasterDispel(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean = ctx.battle is EntityBattleContext && super.isCastPossible(ctx)

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    ctx.world.dispelAll(target, baseChancePercent(ctx.skillLevel))

    return null
  }

  companion object {
    fun baseChancePercent(level: Int): Int = BASE_PERCENT + PERCENT_PER_LEVEL * level.coerceIn(1, MAX_LEVEL)

    private const val BASE_PERCENT = 50
    private const val PERCENT_PER_LEVEL = 10
    private const val MAX_LEVEL = 5
  }
}
