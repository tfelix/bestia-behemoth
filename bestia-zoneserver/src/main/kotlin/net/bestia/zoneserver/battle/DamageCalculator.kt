package net.bestia.zoneserver.battle

interface DamageCalculator {
  fun calculateDamage(battleCtx: BattleContext): Int
}