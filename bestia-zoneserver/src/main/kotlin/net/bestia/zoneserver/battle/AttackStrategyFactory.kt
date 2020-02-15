package net.bestia.zoneserver.battle

import org.springframework.stereotype.Component
import java.util.concurrent.ThreadLocalRandom

@Component
class AttackStrategyFactory {

  private val random = ThreadLocalRandom.current()
  private val magicCalculator = MagicDamageCalculator()
  private val meleeCalculator = MeleePhysicalDamageCalculator()

  fun getAttackStrategy(ctx: BattleContext): AttackStrategy {
    val usedAttack = ctx.usedAttack

    return if (usedAttack.isMagic) {
      MagicPhysicalAttackStrategy(ctx, magicCalculator)
    } else {
      MeleePhysicalAttackStrategy(ctx, meleeCalculator, random)
    }
  }
}
