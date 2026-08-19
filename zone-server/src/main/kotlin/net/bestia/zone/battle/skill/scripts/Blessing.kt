package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.skill.BasicMagicSkillStrategy
import net.bestia.zone.battle.skill.SkillContext
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

/**
 * Blessing (`skills.yml` id 1).
 *
 * The description talks about boosting STR/DEX/INT/HIT, but only speed is modeled as a buffable stat so far -
 * this applies the `BLESSING` status effect (`status_effects.yml` id 5), whose script
 * (`net.bestia.zone.battle.status.scripts.Blessing`) reuses the same speed-multiplier shape as `SWIFTNESS`
 * until a broader stat-modifier is needed.
 */
@Component
class Blessing(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null

    ctx.applyStatusEffect(target, StatusEffectId.BLESSING)

    // The client learns about the buff from the StatusEffects component's own dirty-sync, so there is no
    // health delta to float over the target.
    return null
  }
}
