package net.bestia.zone.battle.attack

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.damage.MeleePhysicalDamageCalculator
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.lang.IllegalStateException
import java.util.concurrent.ThreadLocalRandom

@Component
class AttackStrategyFactory(
  lineOfSightService: LineOfSightService,
  private val applicationContext: ApplicationContext
) {

  private val random = ThreadLocalRandom.current()
  private val meleeCalculator = MeleePhysicalDamageCalculator(random)
  private val meleeStrategy = MeleePhysicalAttackStrategy(meleeCalculator, lineOfSightService, random)
  private val rangedPhysicalStrategy = RangedPhysicalAttackStrategy(meleeCalculator, lineOfSightService, random)

  // private val magicStrategy = MagicAttackStrategy(lineOfSightService, MagicDamageCalculator())

  fun getAttackStrategy(ctx: BattleContext): AttackStrategy {
    return when (ctx.usedAttack.attackType) {
      AttackType.MELEE_PHYSICAL -> meleeStrategy
      AttackType.RANGED_PHYSICAL -> rangedPhysicalStrategy
      AttackType.MAGIC -> TODO()
      AttackType.NO_DAMAGE -> getScriptBasedStrategy(ctx)
    }
  }

  private fun getScriptBasedStrategy(ctx: BattleContext): AttackStrategy {
    val atkScript = ctx.usedAttack.script ?: throw NoAttackScriptException()
    val scriptClassName = "net.bestia.behemoth.battle.attack.scripts.${atkScript}"

    return applicationContext.getBean(scriptClassName) as? AttackStrategy
      ?: throw IllegalStateException("Class $scriptClassName is not of type AttackStrategy")
  }
}
