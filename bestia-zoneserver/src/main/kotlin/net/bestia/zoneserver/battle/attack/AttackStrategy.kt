package net.bestia.zoneserver.battle.attack

import net.bestia.model.battle.Damage
import net.bestia.zoneserver.battle.EntityBattleContext

interface AttackStrategy {
  fun doesAttackHit(battleCtx: EntityBattleContext): Boolean
  fun isCriticalHit(battleCtx: EntityBattleContext): Boolean
  fun doAttack(): List<Damage>
}