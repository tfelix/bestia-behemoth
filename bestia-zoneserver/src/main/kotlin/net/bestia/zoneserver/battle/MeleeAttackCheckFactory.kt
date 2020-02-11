package net.bestia.zoneserver.battle

import net.bestia.model.battle.AttackType
import org.springframework.stereotype.Component

@Component
class MeleeAttackCheckFactory : AttackCheckFactory {
  override fun canBuildFor(battleCtx: BattleContext): Boolean {
    return battleCtx.usedAttack.type == AttackType.MELEE_PHYSICAL ||
        battleCtx.usedAttack.type == AttackType.MELEE_MAGIC
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