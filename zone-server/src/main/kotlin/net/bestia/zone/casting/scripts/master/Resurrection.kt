package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Resurrection (`skills.yml` id 95): raises a fallen master or bestia where it lies with 30% of its HP. Never the
 * caster. Nothing stops two Priests raising the same body: only the caster's own cooldown limits it.
 */
@Component
class Resurrection(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  /** Refused for the living before anything is paid, so a 10 minute cooldown is not spent on nothing. */
  override fun isCastPossible(ctx: SkillContext): Boolean {
    val target = ctx.targetEntityId ?: return false

    return ctx.battle is EntityBattleContext && target != ctx.casterId && ctx.world.isFallen(target) &&
      super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    ctx.world.revive(target, HEALTH_PERCENT)

    return null
  }

  private companion object {
    const val HEALTH_PERCENT = 30
  }
}
