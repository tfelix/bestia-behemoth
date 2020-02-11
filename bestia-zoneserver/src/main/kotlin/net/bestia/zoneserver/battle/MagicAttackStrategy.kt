package net.bestia.zoneserver.battle

abstract class MagicAttackStrategy : AttackStrategy() {
  // Magic attacks usually always hits.
  override fun doesAttackHit(battleCtx: BattleContext): Boolean {
    return true
  }

  // Magic attacks can not hit critical.
  override fun isCriticalHit(battleCtx: BattleContext): Boolean {
    return false
  }
}