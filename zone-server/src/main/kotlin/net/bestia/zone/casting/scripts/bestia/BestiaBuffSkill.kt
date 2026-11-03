package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * The buff archetype: the status its row names, EMPOWERED when it names none, on the caster or the friend it was
 * cast at. Centred on the caster with a reach, like War Cry, it reaches every friend around.
 */
@Component
class BestiaBuffSkill : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    // A rage is paid for with fatigue, and no fresh rage until that is over.
    return !(statusOf(ctx) == StatusEffectId.ENRAGED && ctx.world.hasEffect(ctx.casterId, StatusEffectId.FATIGUE.id))
  }

  override fun execute(ctx: SkillContext): Damage? {
    val status = statusOf(ctx)
    val amount = if (status in ENDOWMENTS) Element.valueOf(ctx.skill.element).ordinal else 0

    recipientsOf(ctx).forEach { ctx.world.applyStatusEffect(it, status.id, ctx.skillLevel, amount) }

    return null
  }

  private fun recipientsOf(ctx: SkillContext): List<Long> {
    val radius = ctx.skill.mechanics.reach.toLong()
    if (!ctx.skill.mechanics.selfCentred || radius <= 0L) {
      // The AI casts every skill at its enemy, so a buff aimed at a foe lands on the caster instead.
      val target = ctx.targetEntityId?.takeUnless { ctx.world.isFoe(it) }

      return listOf(target ?: ctx.casterId)
    }

    val centre = ctx.world.positionOf(ctx.casterId) ?: return listOf(ctx.casterId)

    return ctx.world.entitiesInCube(centre, radius * 2).filter { it == ctx.casterId || !ctx.world.isFoe(it) }
  }

  private fun statusOf(ctx: SkillContext): StatusEffectId {
    return ctx.skill.statusEffect?.let { StatusEffectId.valueOf(it) } ?: StatusEffectId.EMPOWERED
  }

  private companion object {
    val ENDOWMENTS = setOf(StatusEffectId.ENDOWED, StatusEffectId.ARMOR_ENDOWED)
  }
}
