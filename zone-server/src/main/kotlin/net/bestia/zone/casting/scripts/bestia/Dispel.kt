package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Strips a creature of every buff a dispel may lift; wards and guild auras stay, see `status_effects.yml`. Aimed at
 * an enchanted item on the ground, it tries to break each enchantment instead.
 */
@Component
class Dispel(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return (ctx.battle is EntityBattleContext || isAimedAtGroundItem(ctx)) && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null

    if (isAimedAtGroundItem(ctx)) {
      ctx.world.dispelGroundItem(target, groundItemChancePercent(ctx.skillLevel))
    } else {
      ctx.world.dispel(target)
    }

    return null
  }

  /** A ground context keeps its target id only for an enchanted ground item, see SkillContextFactory. */
  private fun isAimedAtGroundItem(ctx: SkillContext): Boolean {
    return ctx.battle is GroundBattleContext && ctx.targetEntityId != null
  }

  companion object {
    /** master.md, Dispel: before the enchantment's strength is taken off. */
    fun groundItemChancePercent(skillLevel: Int): Int {
      return 50 + 10 * skillLevel
    }
  }
}
