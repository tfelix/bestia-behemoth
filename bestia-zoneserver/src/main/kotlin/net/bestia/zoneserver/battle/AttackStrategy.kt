package net.bestia.zoneserver.battle

import net.bestia.model.battle.Damage

abstract class AttackStrategy {
  protected abstract fun doesAttackHit(battleCtx: BattleContext): Boolean
  protected abstract fun isCriticalHit(battleCtx: BattleContext): Boolean
  abstract fun doAttack(): List<Damage>
}