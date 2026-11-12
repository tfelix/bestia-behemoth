package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.scripts.Poison
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Enchant Poison (`skills.yml` id 125): works the poison the cast spends into an ally's weapon for 5 min, see
 * `PoisonedWeapon`. The skill's level sets how often a swing poisons, the poison how hard.
 */
@Component
class EnchantPoison(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    if (ctx.world.isFoe(target)) return null

    val poison = ctx.skill.mechanics.reagents.firstOrNull()?.item
    val drain = DRAIN_PER_MILLE[poison] ?: Poison.DEFAULT_DRAIN_PER_MILLE
    ctx.world.applyStatusEffect(target, StatusEffectId.ENCHANT_POISON.id, ctx.skillLevel, amount = drain)

    return null
  }

  companion object {
    /** How hard each brewed poison bites, per tick in per mille of the maximum HP. A stronger poison adds a row. */
    val DRAIN_PER_MILLE = mapOf("basic_poison" to Poison.DEFAULT_DRAIN_PER_MILLE)
  }
}
