package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.battle.CriticalHit
import net.bestia.model.battle.Damage
import net.bestia.model.battle.Miss
import net.bestia.model.battle.NormalHit
import java.util.*

private val LOG = KotlinLogging.logger { }

class MeleePhysicalAttackStrategy(
    private val battleCtx: BattleContext,
    private val damageCalculator: PhysicalDamageCalculator,
    random: Random
) : PhysicalAttackStrategy(random) {

  /**
   * Checks if the attack performs a critical hit onto the target. The outcome
   * of the critical hit check is then saved into damage variables.
   */
  override fun doAttack(): List<Damage> {
    val isCritical = isCriticalHit(battleCtx)

    if (!doesAttackHit(battleCtx)) {
      return listOf(Miss)
    }

    val damageValue = damageCalculator.calculateDamage(battleCtx)
    LOG.trace("Primary damage calculated: {}", damageValue)

    val primaryDamage = when (isCritical) {
      true -> CriticalHit(damageValue)
      false -> NormalHit(damageValue)
    }

    return listOf(primaryDamage)
  }
}