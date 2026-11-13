package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Dispel (`skills.yml` id 64), the Sage's: buffs and curses alike, `(50 + 10/lv)%` each, cut by a stronger spell, or a
 * Spell Binding Magic Sense showed. The Yellow Mana Crystal is the skill's reagent. A bestia's Dispel is
 * `scripts.bestia.Dispel`.
 */
@Component
class MasterDispel(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  /** A Spell Binding's glyph or bound item has no body, so it is aimed at as the ground it lies on. */
  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.targetEntityId != null && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    val baseChance = baseChancePercent(ctx.skillLevel)
    if (ctx.world.dispelBinding(target, baseChance) == null) {
      ctx.world.dispelAll(target, baseChance)
    }

    return null
  }

  companion object {
    fun baseChancePercent(level: Int): Int = BASE_PERCENT + PERCENT_PER_LEVEL * level.coerceIn(1, MAX_LEVEL)

    private const val BASE_PERCENT = 50
    private const val PERCENT_PER_LEVEL = 10
    private const val MAX_LEVEL = 5
  }
}
