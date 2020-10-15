package net.bestia.zoneserver.battle.attack

import net.bestia.model.battle.AttackType
import net.bestia.zoneserver.battle.BattleContext
import net.bestia.zoneserver.battle.EntityBattleContext
import org.springframework.stereotype.Component

@Component
class MeleeAttackCheckFactory : AttackCheckFactory {
  override fun canBuildFor(battleCtx: BattleContext): Boolean {
    if (battleCtx !is EntityBattleContext) {
      return false
    }

    return battleCtx.usedAttack.attackType == AttackType.MELEE_PHYSICAL
  }

  override fun buildCheckFor(battleCtx: BattleContext): AttackCheck {
    battleCtx as EntityBattleContext

    return listOfNotNull(
        IsEntityAttackableCheck(battleCtx),
        ManaAttackCheck(battleCtx),
        DistanceAttackCheck(battleCtx),
        if (battleCtx.usedAttack.needsLineOfSight) {
          LineOfSightAttackCheck()
        } else {
          null
        }
    ).reduceRight { a, b ->
      // This is broken
      a.addCheck(b)
      b
    }
  }
}