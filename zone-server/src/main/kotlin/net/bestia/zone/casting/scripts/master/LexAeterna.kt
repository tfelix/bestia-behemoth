package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/** Lex Aeterna (`skills.yml` id 91): marks a foe with [StatusEffectId.LEX_AETERNA]. */
@Component
class LexAeterna(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean = ctx.battle is EntityBattleContext && super.isCastPossible(ctx)

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    ctx.applyStatusEffect(target, StatusEffectId.LEX_AETERNA)

    return null
  }
}
