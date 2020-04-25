package net.bestia.zoneserver.battle.damage

import mu.KotlinLogging
import net.bestia.model.battle.Damage
import net.bestia.model.battle.Hit
import net.bestia.zoneserver.battle.AttackStrategy
import net.bestia.zoneserver.battle.BattleContext

private val LOG = KotlinLogging.logger { }

class MagicPhysicalAttackStrategy(
    private val battleCtx: BattleContext,
    private val damageCalculator: MagicDamageCalculator
) : AttackStrategy() {
  // Magic always hits
  override fun doesAttackHit(battleCtx: BattleContext): Boolean {
    return true
  }

  // Magic never does a crit
  override fun isCriticalHit(battleCtx: BattleContext): Boolean {
    return false
  }

  /**
   * Checks if the attack performs a critical hit onto the target. The outcome
   * of the critical hit check is then saved into damage variables.
   */
  override fun doAttack(): List<Damage> {
    val damageValue = damageCalculator.calculateDamage(battleCtx)
    LOG.trace("Primary damage calculated: {}", damageValue)

    return listOf(Hit(damageValue))
  }
}