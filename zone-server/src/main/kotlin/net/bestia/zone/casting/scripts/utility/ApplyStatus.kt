package net.bestia.zone.casting.scripts.utility

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * Puts the status its row names on the friend it was cast at, the caster itself when none was picked. This is
 * how a caught bestia keeps its master warm, fast or blessed.
 */
@Component
class ApplyStatus : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.skill.statusEffect != null
  }

  override fun execute(ctx: SkillContext): Damage? {
    val status = StatusEffectId.valueOf(checkNotNull(ctx.skill.statusEffect))
    ctx.applyStatusEffect(ctx.targetEntityId ?: ctx.casterId, status)

    return null
  }
}
