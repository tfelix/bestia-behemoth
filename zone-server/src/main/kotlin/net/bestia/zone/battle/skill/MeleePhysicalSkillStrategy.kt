package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.*
import java.util.*

open class MeleePhysicalSkillStrategy(
  private val damageCalculator: MeleePhysicalDamageCalculator,
  private val losService: LineOfSightService,
  random: Random
) : PhysicalSkillStrategy(losService, random) {

  /**
   * Checks if the attack performs a critical hit onto the target. The outcome
   * of the critical hit check is then saved into damage variables.
   */
  override fun doAttack(ctx: BattleContext): Damage {
    val damageValue = damageCalculator.calculateDamage(ctx)
    LOG.trace { "doAttack: damage $damageValue" }

    val primaryDamage = when (isCriticalHit(ctx)) {
      true -> CriticalHit(damageValue)
      false -> HitDamage(damageValue)
    }

    return primaryDamage
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
