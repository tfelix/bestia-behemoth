package net.bestia.zoneserver.battle

import org.springframework.stereotype.Component

// TODO find besser name
@Component
class AttackCheckFactoryImpl(
    private val attackCheckFactories: List<AttackCheckFactory>
) {

  fun buildChecker(battleCtx: BattleContext): AttackCheck {
    val fac = attackCheckFactories.find { it.canBuildFor(battleCtx) }
        ?: throw IllegalStateException("No factory found for $battleCtx")

    return fac.buildCheckFor(battleCtx)
  }
}