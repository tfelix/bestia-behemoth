package net.bestia.zoneserver.battle

interface AttackCheckFactory {
  fun buildCheckFor(battleCtx: BattleContext): AttackCheck
  fun canBuildFor(battleCtx: BattleContext): Boolean
}