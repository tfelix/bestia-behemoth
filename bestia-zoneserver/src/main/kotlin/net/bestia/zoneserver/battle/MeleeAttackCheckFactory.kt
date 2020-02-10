package net.bestia.zoneserver.battle

import org.springframework.stereotype.Component

@Component
class MeleeAttackCheckFactory : AttackCheckFactory {
  override fun canBuildFor(battleCtx: BattleContext): Boolean {
    return !battleCtx.usedAttack.isRanged
  }

  override fun buildCheckFor(battleCtx: BattleContext): AttackCheck {
    return listOfNotNull(
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