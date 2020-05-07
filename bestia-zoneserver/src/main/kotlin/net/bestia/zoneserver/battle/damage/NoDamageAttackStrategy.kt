package net.bestia.zoneserver.battle.damage

import net.bestia.model.battle.Damage
import net.bestia.zoneserver.battle.AttackStrategy
import net.bestia.zoneserver.battle.BattleContext

class NoDamageAttackStrategy(
    private val battleCtx: BattleContext
) : AttackStrategy() {
  override fun doesAttackHit(battleCtx: BattleContext): Boolean {
    return true
  }

  override fun isCriticalHit(battleCtx: BattleContext): Boolean {
    return false
  }

  override fun doAttack(): List<Damage> {
    return listOf()
  }
}