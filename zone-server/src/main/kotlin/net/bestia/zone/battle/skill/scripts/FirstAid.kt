package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.skill.BasicMagicSkillStrategy
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component
import kotlin.math.max

/**
 * First Aid (`skills.yml` id 8): bandages channelled over ten seconds, healing a share of the target's
 * maximum health or a flat amount, whichever is more.
 *
 * The flat floor is what makes the skill worth casting on a low-level bestia, whose percentage of a
 * small pool would be nothing; the percentage is what keeps it worth casting later. Both come from the
 * skill's own description.
 *
 * A bestia may only receive it once a minute, which is [FirstAidCooldown] on the target rather than a
 * cooldown on the caster - see [effectsOnTarget]. The three-minute cooldown the description also
 * promises is presentation-only for now: nothing server-side tracks per-skill cooldowns.
 */
@Component
class FirstAid(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun isAttackPossible(ctx: BattleContext): Boolean {
    if (ctx !is EntityBattleContext) {
      return false
    }

    // Refused rather than wasted: the channel is ten seconds long, so letting it run and then heal for
    // nothing would be the worse outcome. Checked again here at resolution time, which is what stops a
    // second caster sneaking in while the first is still channelling.
    if (StatusEffectId.FIRST_AID_COOLDOWN.id in ctx.defender.activeEffectIds) {
      return false
    }

    return super.isAttackPossible(ctx)
  }

  override fun doAttack(ctx: BattleContext): Damage {
    return when (ctx) {
      is EntityBattleContext -> Heal(healed(ctx))
      is GroundBattleContext -> Miss
    }
  }

  override fun effectsOnTarget(ctx: BattleContext) = listOf(StatusEffectId.FIRST_AID_COOLDOWN)

  private fun healed(ctx: EntityBattleContext): Int {
    val level = ctx.usedAttack.level.coerceIn(1, PERCENT_OF_MAX_HP.size)
    val fromPool = ctx.defender.maxHealth * PERCENT_OF_MAX_HP[level - 1] / 100

    return max(fromPool, FLAT_HP[level - 1])
  }

  private companion object {
    /** Per rank, from the skill's description: 30% / 60% / 100% of the target's maximum health. */
    val PERCENT_OF_MAX_HP = intArrayOf(30, 60, 100)

    /** The floor for each of those ranks, for a target whose pool is still small. */
    val FLAT_HP = intArrayOf(100, 200, 350)
  }
}
