package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Gut Punch (`skills.yml` id 129): stuns a foe for `3 s + 1 s/lv`, a boss for half as long. The table has no chance,
 * so the stun always lands; only an immunity turns it away.
 */
@Component
class GutPunch(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)
    val isBoss = ctx.world.natureOf(target)?.boss == true

    ctx.world.applyStatusEffect(target, StatusEffectId.STUN.id, level, durationSeconds = stunSeconds(level, isBoss))

    return null
  }

  companion object {
    private const val MAX_LEVEL = 3

    fun stunSeconds(level: Int, isBoss: Boolean): Double {
      val seconds = 3.0 + level.coerceIn(1, MAX_LEVEL)

      return if (isBoss) seconds / 2 else seconds
    }
  }
}
