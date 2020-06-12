package net.bestia.zoneserver.battle.damage

import net.bestia.model.battle.Damage
import net.bestia.zoneserver.battle.AttackStrategy
import net.bestia.zoneserver.battle.EntityBattleContext

class NoDamageAttackStrategy(
    private val battleCtx: EntityBattleContext
) : AttackStrategy() {
  override fun doesAttackHit(battleCtx: EntityBattleContext): Boolean {
    return true
  }

  override fun isCriticalHit(battleCtx: EntityBattleContext): Boolean {
    return false
  }

  override fun doAttack(): List<Damage> {
    return listOf()
  }
}