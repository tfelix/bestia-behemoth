package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.CriticalHit
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.MeleePhysicalDamageCalculator
import net.bestia.zone.battle.damage.Miss
import java.util.Random

/**
 * An arrow or a bolt: the same physical formula as a melee swing, but it needs something to hit. Loosing
 * one at open ground is refused rather than resolved into a miss - there is nothing there to shoot at.
 */
class RangedPhysicalAttackStrategy(
  private val damageCalculator: MeleePhysicalDamageCalculator,
  losService: LineOfSightService,
  random: Random
) : PhysicalAttackStrategy(losService, random) {

  override fun isAttackPossible(ctx: BattleContext): Boolean {
    return when (ctx) {
      is EntityBattleContext -> super.isAttackPossible(ctx)
      is GroundBattleContext -> false
    }
  }

  override fun execute(ctx: BattleContext): Damage {
    if (ctx is GroundBattleContext || isMiss(ctx)) {
      return Miss
    }

    val damageValue = damageCalculator.calculateDamage(ctx)
    LOG.trace { "ranged attack: damage $damageValue" }

    return if (isCriticalHit(ctx)) CriticalHit(damageValue) else HitDamage(damageValue)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
