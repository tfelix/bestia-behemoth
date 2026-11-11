package net.bestia.zone.casting.scripts

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.scripts.BlessingStatusEffect
import org.springframework.stereotype.Component

/**
 * Blessing (`skills.yml` id 6), on a single target.
 *
 * Applies the `BLESSING` status effect (`status_effects.yml` id 5) at the cast level. What the effect does to
 * the target's stats, and for how long, is `net.bestia.zone.battle.status.scripts.BlessingStatusEffect`.
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

    // The curse is harm, so it only lands where a blow would: never on an ally's undead bestia or in a safe zone.
    if (BlessingStatusEffect.curses(ctx.world.natureOf(target)) && !ctx.world.isFoe(target)) {
      return null
    }

    ctx.applyStatusEffect(target, StatusEffectId.BLESSING)

    // The client learns about the buff from the StatusEffects component's own dirty-sync, so there is no
    // health delta to float over the target.
    return null
  }
}
