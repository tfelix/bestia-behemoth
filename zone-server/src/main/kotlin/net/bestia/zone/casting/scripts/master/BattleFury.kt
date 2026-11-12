package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/** Battle Fury (`skills.yml` id 147): the Brawler goes [StatusEffectId.BERSERK] for `10 s/lv`. */
@Component
class BattleFury : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)
    ctx.world.applyStatusEffect(ctx.casterId, StatusEffectId.BERSERK.id, level, durationSeconds = durationSeconds(level))

    return null
  }

  companion object {
    private const val MAX_LEVEL = 3

    fun durationSeconds(level: Int): Double {
      return 10.0 * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
