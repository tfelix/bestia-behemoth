package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/** Magnificat (`skills.yml` id 92): [StatusEffectId.MAGNIFICAT] on the caster's party in sight and their bestias. */
@Component
class Magnificat : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean = true

  override fun execute(ctx: SkillContext): Damage? {
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)
    ctx.world.partyNear().forEach { ctx.world.applyStatusEffect(it, StatusEffectId.MAGNIFICAT.id, level) }

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
  }
}
