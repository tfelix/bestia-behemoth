package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.skill.BasicMagicSkillStrategy
import net.bestia.zone.battle.skill.SkillContext
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component
import kotlin.math.max

/**
 * First Aid (`skills.yml` id 8): bandages channelled over ten seconds, healing a share of the target's
 * maximum health or a flat amount, whichever is more.
 *
 * The flat floor is what makes the skill worth casting on a low-level bestia, whose percentage of a small pool
 * would be nothing; the percentage is what keeps it worth casting later. Both come from the skill's own
 * description.
 *
 * A bestia may only receive it once a minute, which is `FIRST_AID_COOLDOWN` on the *target* rather than a
 * cooldown on the caster - so two healers cannot take turns topping the same bestia up. The three-minute
 * cooldown the description also promises is presentation-only for now: nothing server-side tracks per-skill
 * cooldowns.
 */
@Component
class FirstAid(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val battle = ctx.battle
    if (battle !is EntityBattleContext) {
      return false
    }

    // Refused rather than wasted: the channel is ten seconds long, so letting it run and then heal for
    // nothing would be the worse outcome. This reads the snapshot, so it is the cheap early-out only - the
    // gate that actually holds is the live re-check in `execute`.
    if (StatusEffectId.FIRST_AID_COOLDOWN.id in battle.defender.activeEffectIds) {
      return false
    }

    return super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null
    val target = ctx.targetEntityId ?: return null

    // Computed before the claim, so the mark can never affect what was healed.
    val healed = Heal(healed(battle))

    // The mark is claimed atomically, and losing the claim is what fizzles the cast: casts resolve off the
    // tick thread, so two healers who both started channelling on an untreated bestia both pass the snapshot
    // check above. Exactly one of them gets the mark, and only that one heals.
    val claimed = ctx.world.applyStatusEffectIfAbsent(
      target,
      StatusEffectId.FIRST_AID_COOLDOWN.id,
      ctx.skillLevel
    )

    return if (claimed) healed else null
  }

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
