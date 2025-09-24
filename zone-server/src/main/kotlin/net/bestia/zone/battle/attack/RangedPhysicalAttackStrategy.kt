package net.bestia.zone.battle.attack

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.*
import java.util.*

class RangedPhysicalAttackStrategy(
  private val damageCalculator: MeleePhysicalDamageCalculator,
  losService: LineOfSightService,
  random: Random
) : PhysicalAttackStrategy(losService, random) {
  override fun isAttackPossible(ctx: BattleContext): Boolean {
    return when (ctx) {
      is EntityBattleContext -> isAttackInRange(ctx)
      is GroundBattleContext -> false
    }
  }

  /**
   * Checks if the attack performs a critical hit onto the target. The outcome
   * of the critical hit check is then saved into damage variables.
   */
  override fun doAttack(ctx: BattleContext): Damage {
    return when (ctx) {
      is EntityBattleContext -> doRangedAttack(ctx)
      is GroundBattleContext -> return Miss
    }
  }

  private fun doRangedAttack(ctx: EntityBattleContext): Damage {
    val damageValue = damageCalculator.calculateDamage(ctx)
    LOG.trace { "doRangedAttack: damage $damageValue" }

    return when (isCriticalHit(ctx)) {
      true -> CriticalHit(damageValue)
      false -> HitDamage(damageValue)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}