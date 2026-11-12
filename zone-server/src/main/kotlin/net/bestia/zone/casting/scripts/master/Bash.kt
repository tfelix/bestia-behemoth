package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Blocked
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Bash (`skills.yml` id 136): a weapon blow at `120% + 20%/lv` ATK. A blow that lands stuns for 1 s at `5%/lv` on top
 * of the stun chance the Knight's gear gives every swing, against the target's resistance.
 */
@Component
class Bash(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    val blow = ctx.world.strike(target, ctx.battle.usedAttack, atkPercent(ctx.skillLevel)) ?: return null
    if (blow == Miss || blow == Blocked) return null

    val gearChance = ctx.world.swingStatusChanceOf(ctx.casterId, StatusEffectId.STUN)
    ctx.world.inflict(target, StatusEffectId.STUN.name, gearChance + stunChancePercent(ctx.skillLevel), STUN_SECONDS, level = 1)

    return null
  }

  companion object {
    private const val MAX_LEVEL = 5
    const val STUN_SECONDS = 1.0

    fun atkPercent(level: Int): Int {
      return 100 + 20 * level.coerceIn(1, MAX_LEVEL)
    }

    fun stunChancePercent(level: Int): Int {
      return 5 * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
