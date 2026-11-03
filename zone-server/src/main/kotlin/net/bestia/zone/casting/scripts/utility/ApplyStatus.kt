package net.bestia.zone.casting.scripts.utility

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * Puts the status its row names on the friend it was cast at, the caster itself when none was picked. This is
 * how a caught bestia keeps its master warm, fast or blessed. A row may name its status as an on-hit status instead, to give
 * it a level of its own, like Greater Blessing's Blessing 10.
 */
@Component
class ApplyStatus : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.skill.statusEffect != null || ctx.skill.mechanics.onHit.isNotEmpty()
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: ctx.casterId

    ctx.skill.statusEffect?.let { ctx.applyStatusEffect(target, StatusEffectId.valueOf(it)) }
    ctx.skill.mechanics.onHit.forEach { hitStatus ->
      val level = if (hitStatus.level > 0) hitStatus.level else ctx.skillLevel
      ctx.world.applyStatusEffect(target, StatusEffectId.valueOf(hitStatus.status).id, level)
    }

    return null
  }
}
