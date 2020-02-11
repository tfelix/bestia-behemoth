package net.bestia.zoneserver.battle

import org.springframework.stereotype.Component
import java.lang.IllegalStateException
import java.util.concurrent.ThreadLocalRandom

@Component
class AttackStrategyFactory {

  private val random = ThreadLocalRandom.current()

  fun getAttackStrategy(ctx: BattleContext): AttackStrategy {
    val usedAttack = ctx.usedAttack

    if (usedAttack.isMagic) {
      if (usedAttack.isRanged) {
        throw IllegalStateException("Not supported")
      } else {
        throw IllegalStateException("Not supported")
      }
    } else {
      if (usedAttack.isRanged) {
        throw IllegalStateException("Not supported")
      } else {
        return MeleePhysicalAttackStrategy(ctx, PhysicalDamageCalculator(random), random)
      }
    }
  }
}

