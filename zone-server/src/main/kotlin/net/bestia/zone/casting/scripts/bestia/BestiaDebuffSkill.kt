package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * The debuff archetype: rolls its row's statuses on the target, WEAKENED when it names none. Centred on the caster
 * with a reach, like Growl or a roar, it rolls them on every foe around instead.
 */
@Component
class BestiaDebuffSkill(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    targetsOf(ctx).forEach { target ->
      if (ctx.skill.mechanics.onHit.isEmpty()) {
        ctx.applyStatusEffect(target, StatusEffectId.WEAKENED)
      } else {
        BestiaSkillMechanics.inflict(ctx, target)
      }
    }

    return null
  }

  private fun targetsOf(ctx: SkillContext): List<Long> {
    val radius = ctx.skill.mechanics.reach.toLong()
    if (!ctx.skill.mechanics.selfCentred || radius <= 0L) return listOfNotNull(ctx.targetEntityId)

    val centre = ctx.world.positionOf(ctx.casterId) ?: return emptyList()

    return ctx.world.entitiesInCube(centre, radius * 2).filter { ctx.world.isFoe(it) }
  }
}
