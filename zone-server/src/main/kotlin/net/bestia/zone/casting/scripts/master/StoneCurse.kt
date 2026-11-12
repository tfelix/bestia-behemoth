package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Stone Curse (`skills.yml` id 104): a `20%/lv` roll for [StatusEffectId.STONING], whose Stone then lasts `15 s/lv`.
 * The Red Mana Crystal is the skill's reagent, spent whether the roll wins or not. A bestia's is `BestiaDebuffSkill`.
 */
@Component
class StoneCurse(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    ctx.world.inflict(
      target,
      StatusEffectId.STONING.name,
      chancePercent = CHANCE_PERCENT_PER_LEVEL * level,
      durationSeconds = null,
      level = level,
      amount = STONE_SECONDS_PER_LEVEL * level,
    )

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
    const val CHANCE_PERCENT_PER_LEVEL = 20
    const val STONE_SECONDS_PER_LEVEL = 15
  }
}
