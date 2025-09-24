package net.bestia.zone.battle.damage

import net.bestia.zone.battle.EntityBattleContext

interface DamageCalculator {
  fun calculateDamage(battleCtx: EntityBattleContext): Int
}