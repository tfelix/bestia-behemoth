package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/** Kyrie Eleison (`skills.yml` id 83): raises the [StatusEffectId.KYRIE_ELEISON] ward on an ally. */
@Component
class KyrieEleison(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean = ctx.battle is EntityBattleContext && super.isCastPossible(ctx)

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null

    // A refresh only resets the clock, so the old ward goes first and a recast brings back all of its hits.
    ctx.world.removeEffect(target, StatusEffectId.KYRIE_ELEISON.id)
    ctx.world.applyStatusEffect(target, StatusEffectId.KYRIE_ELEISON.id, level = ctx.skillLevel.coerceIn(1, MAX_LEVEL))

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
  }
}
