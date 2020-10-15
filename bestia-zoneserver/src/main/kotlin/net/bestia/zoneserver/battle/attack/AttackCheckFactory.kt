package net.bestia.zoneserver.battle.attack

import net.bestia.zoneserver.battle.BattleContext

interface AttackCheckFactory {
  fun buildCheckFor(battleCtx: BattleContext): AttackCheck
  fun canBuildFor(battleCtx: BattleContext): Boolean
}