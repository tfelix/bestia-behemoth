package net.bestia.zoneserver.battle

import net.bestia.model.battle.AttackType
import org.springframework.stereotype.Component

@Component
class MeleeAttackCheckFactory : AttackCheckFactory {
  override fun canBuildFor(battleCtx: BattleContext): Boolean {
    return battleCtx.usedAttack.attackType == AttackType.MELEE_PHYSICAL
  }

  override fun buildCheckFor(battleCtx: BattleContext): AttackCheck {
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