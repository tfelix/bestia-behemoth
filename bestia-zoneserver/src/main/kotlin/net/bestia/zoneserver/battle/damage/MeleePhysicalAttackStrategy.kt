package net.bestia.zoneserver.battle.damage

import mu.KotlinLogging
import net.bestia.model.battle.CriticalHit
import net.bestia.model.battle.Damage
import net.bestia.model.battle.Miss
import net.bestia.model.battle.Hit
import net.bestia.zoneserver.battle.EntityBattleContext
import net.bestia.zoneserver.battle.attack.PhysicalAttackStrategy
import java.util.*

private val LOG = KotlinLogging.logger { }

class MeleePhysicalAttackStrategy(
    private val battleCtx: EntityBattleContext,
    private val damageCalculator: MeleePhysicalDamageCalculator,
    random: Random
) : PhysicalAttackStrategy(random) {

  /**
   * Checks if the attack performs a critical hit onto the target. The outcome
   * of the critical hit check is then saved into damage variables.
   */
  override fun doAttack(): List<Damage> {
    // TODO Check einbauen ob die Attacke trifft, also den ganzen flow!

    val isCritical = isCriticalHit(battleCtx)

    if (!doesAttackHit(battleCtx)) {
      return listOf(Miss)
    }

    val damageValue = damageCalculator.calculateDamage(battleCtx)
    LOG.trace("Primary damage calculated: {}", damageValue)

    val primaryDamage = when (isCritical) {
      true -> CriticalHit(damageValue)
      false -> Hit(damageValue)
    }

    return listOf(primaryDamage)
  }
}