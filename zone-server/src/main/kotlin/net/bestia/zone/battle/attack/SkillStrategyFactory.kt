package net.bestia.zone.battle.attack

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.damage.MeleePhysicalDamageCalculator
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.lang.IllegalStateException
import java.util.concurrent.ThreadLocalRandom

@Component
class SkillStrategyFactory(
  lineOfSightService: LineOfSightService,
  private val applicationContext: ApplicationContext
) {

  private val random = ThreadLocalRandom.current()
  private val meleeCalculator = MeleePhysicalDamageCalculator(random)
  private val meleeStrategy = MeleePhysicalSkillStrategy(meleeCalculator, lineOfSightService, random)
  private val rangedPhysicalStrategy = RangedPhysicalSkillStrategy(meleeCalculator, lineOfSightService, random)

  // private val magicStrategy = MagicAttackStrategy(lineOfSightService, MagicDamageCalculator())

  fun getSkillStrategy(ctx: BattleContext): SkillStrategy {
    return when (ctx.usedAttack.skillType) {
      SkillType.MELEE_PHYSICAL -> meleeStrategy
      SkillType.RANGED_PHYSICAL -> rangedPhysicalStrategy
      SkillType.MAGIC -> TODO()
      SkillType.NO_DAMAGE -> getScriptBasedStrategy(ctx)
    }
  }

  private fun getScriptBasedStrategy(ctx: BattleContext): SkillStrategy {
    val atkScript = ctx.usedAttack.script ?: throw NoSkillScriptException()
    val scriptClassName = "net.bestia.behemoth.battle.attack.scripts.${atkScript}"

    return applicationContext.getBean(scriptClassName) as? SkillStrategy
      ?: throw IllegalStateException("Class $scriptClassName is not of type SkillStrategy")
  }
}
