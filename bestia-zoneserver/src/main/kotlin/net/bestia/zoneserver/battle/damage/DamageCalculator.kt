package net.bestia.zoneserver.battle.damage

import net.bestia.zoneserver.battle.EntityBattleContext

interface DamageCalculator {
  fun calculateDamage(battleCtx: EntityBattleContext): Int
}