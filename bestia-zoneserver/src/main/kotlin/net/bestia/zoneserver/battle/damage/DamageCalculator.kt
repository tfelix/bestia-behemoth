package net.bestia.zoneserver.battle.damage

import net.bestia.zoneserver.battle.BattleContext

interface DamageCalculator {
  fun calculateDamage(battleCtx: BattleContext): Int
}