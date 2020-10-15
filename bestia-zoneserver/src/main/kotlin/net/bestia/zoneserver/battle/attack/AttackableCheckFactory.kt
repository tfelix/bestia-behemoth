package net.bestia.zoneserver.battle.attack

import net.bestia.zoneserver.battle.BattleContext
import org.springframework.stereotype.Component

@Component
class AttackableCheckFactory(
    private val attackCheckFactories: List<AttackCheckFactory>
) {

  fun buildChecker(battleCtx: BattleContext): AttackCheck {
    val fac = attackCheckFactories.find { it.canBuildFor(battleCtx) }
        ?: throw IllegalStateException("No factory found for $battleCtx")

    return fac.buildCheckFor(battleCtx)
  }
}