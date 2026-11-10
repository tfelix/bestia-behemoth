package net.bestia.zone.casting.scripts

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillLevelCost
import net.bestia.zone.skill.persistence.Skill
import org.springframework.stereotype.Component
import kotlin.math.min

/**
 * First Aid (`skills.yml` id 8): bandages channelled over ten seconds, healing a share of the target's
 * maximum health or a flat amount, whichever is smaller. The flat value caps it, so a big pool cannot turn a
 * bandage into a full heal.
 *
 * A bestia may only receive it once a minute, which is `FIRST_AID_COOLDOWN` on the *target* rather than a
 * cooldown on the caster - so two healers cannot take turns topping the same bestia up.
 */
@Component
class FirstAid(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun costOf(skill: Skill, level: Int): SkillLevelCost {
    val rank = level.coerceIn(1, MANA_COST.size)

    return SkillLevelCost.of(skill).copy(manaCost = MANA_COST[rank - 1])
  }

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

    // The live claim, not the snapshot check in `isCastPossible`, decides: only the cast that sets the mark heals.
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

    return min(fromPool, FLAT_HP[level - 1])
  }

  private companion object {
    /** Per rank, from the skill's description: 30% / 60% / 100% of the target's maximum health. */
    val PERCENT_OF_MAX_HP = intArrayOf(30, 60, 100)

    /** The cap for each of those ranks, so a large pool does not turn a bandage into a full heal. */
    val FLAT_HP = intArrayOf(100, 200, 350)

    val MANA_COST = intArrayOf(12, 16, 20)
  }
}
