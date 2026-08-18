package net.bestia.zone.battle.skill

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.damage.MeleePhysicalDamageCalculator
import org.springframework.stereotype.Component
import java.lang.IllegalStateException
import java.util.concurrent.ThreadLocalRandom

@Component
class AttackStrategyFactory(
  lineOfSightService: LineOfSightService,
) {

  private val random = ThreadLocalRandom.current()
  private val meleeCalculator = MeleePhysicalDamageCalculator(random)
  private val meleeStrategy = MeleePhysicalSkillStrategy(meleeCalculator, lineOfSightService, random)
  private val rangedPhysicalStrategy = RangedPhysicalSkillStrategy(meleeCalculator, lineOfSightService, random)

  // private val magicStrategy = MagicAttackStrategy(lineOfSightService, MagicDamageCalculator())

  fun getSkillStrategy(ctx: BattleContext): SkillStrategy {
    return when (ctx.usedAttack.attackType) {
      AttackType.MELEE_PHYSICAL -> meleeStrategy
      AttackType.RANGED_PHYSICAL -> rangedPhysicalStrategy
      AttackType.MAGIC -> TODO()
    }
  }
}
