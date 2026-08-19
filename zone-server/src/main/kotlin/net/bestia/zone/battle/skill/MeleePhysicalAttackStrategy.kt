package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.CriticalHit
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.MeleePhysicalDamageCalculator
import net.bestia.zone.battle.damage.Miss
import java.util.Random

open class MeleePhysicalAttackStrategy(
  private val damageCalculator: MeleePhysicalDamageCalculator,
  losService: LineOfSightService,
  random: Random
) : PhysicalAttackStrategy(losService, random) {

  override fun execute(ctx: BattleContext): Damage {
    if (isMiss(ctx)) {
      return Miss
    }

    val damageValue = damageCalculator.calculateDamage(ctx)
    LOG.trace { "melee attack: damage $damageValue" }

    return if (isCriticalHit(ctx)) CriticalHit(damageValue) else HitDamage(damageValue)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
