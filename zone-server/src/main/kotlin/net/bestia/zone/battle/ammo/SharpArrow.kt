package net.bestia.zone.battle.ammo

import net.bestia.zone.battle.BattleContext
import org.springframework.stereotype.Component

/** A barbed head that finds the gaps in a hide: the shot is likelier to crit. */
@Component
class SharpArrow : AmmoEffect {

  override fun beforeHit(ctx: BattleContext) {
    ctx.damageVariables.criticalChanceBonus += CRIT_BONUS_PERCENT
  }

  companion object {
    /** RO's Sharp Arrow: +20 CRIT. */
    const val CRIT_BONUS_PERCENT = 20
  }
}
