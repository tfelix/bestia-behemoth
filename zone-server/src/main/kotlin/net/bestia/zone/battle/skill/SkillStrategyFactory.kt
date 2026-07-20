package net.bestia.zone.battle.skill

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.damage.MeleePhysicalDamageCalculator
import org.springframework.stereotype.Component
import java.lang.IllegalStateException
import java.util.concurrent.ThreadLocalRandom

@Component
class SkillStrategyFactory(
  lineOfSightService: LineOfSightService,
  private val skillScriptRegistry: SkillScriptRegistry
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
      SkillType.PASSIVE -> throw IllegalStateException("PASSIVE skills are always-on and cannot be used as an active attack")
    }
  }

  private fun getScriptBasedStrategy(ctx: BattleContext): SkillStrategy {
    val atkScript = ctx.usedAttack.script ?: throw NoSkillScriptException()

    return skillScriptRegistry.get(atkScript)
      ?: throw IllegalStateException("No SkillStrategy bean registered for script '$atkScript'")
  }
}
