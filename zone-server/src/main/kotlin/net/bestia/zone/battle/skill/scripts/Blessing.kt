package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Buff
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.skill.BasicMagicSkillStrategy
import org.springframework.stereotype.Component

/**
 * Registered under the script name `Blessing` (see `skills.yml` id 1) via
 * [net.bestia.zone.battle.skill.SkillScriptRegistry].
 *
 * `skills.yml`'s description talks about boosting STR/DEX/INT/HIT, but [net.bestia.zone.battle.status.StatType]
 * only models `SPEED` so far - this applies the `BLESSING` status effect (`status_effects.yml` id 5), which
 * reuses the same SPEED-multiplier shape as `SWIFTNESS` until a broader stat-modifier system exists.
 */
@Component
class Blessing(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun doAttack(ctx: BattleContext): Damage {
    return when (ctx) {
      is EntityBattleContext -> Buff(effectId = BLESSING_EFFECT_ID)
      is GroundBattleContext -> Miss
    }
  }

  override fun isAttackPossible(ctx: BattleContext): Boolean {
    return if (ctx is EntityBattleContext) {
      super.isAttackPossible(ctx)
    } else {
      false
    }
  }

  companion object {
    private const val BLESSING_EFFECT_ID = 5L
  }
}
