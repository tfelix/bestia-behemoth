package net.bestia.zoneserver.battle

import net.bestia.model.battle.Damage

abstract class AttackStrategy {
  protected abstract fun doesAttackHit(battleCtx: EntityBattleContext): Boolean
  protected abstract fun isCriticalHit(battleCtx: EntityBattleContext): Boolean
  abstract fun doAttack(): List<Damage>
}