package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/** Gloria (`skills.yml` id 78): [StatusEffectId.GLORIA] on the caster's party in sight and their bestias. */
@Component
class Gloria : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean = true

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.partyNear().forEach { ctx.world.applyStatusEffect(it, StatusEffectId.GLORIA.id, level = 1) }

    return null
  }
}
