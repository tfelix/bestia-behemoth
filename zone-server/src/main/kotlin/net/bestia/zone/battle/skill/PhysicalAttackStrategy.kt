package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.util.clamp
import java.util.Random

abstract class PhysicalAttackStrategy(
  private val losService: LineOfSightService,
  private val random: Random
) : AttackStrategy {

  protected fun isCriticalHit(ctx: BattleContext): Boolean {
    return when (ctx) {
      is EntityBattleContext -> isCriticalHit(ctx)
      // Nothing on open ground has a level or an agility to crit against.
      is GroundBattleContext -> false
    }
  }

  private fun isCriticalHit(ctx: EntityBattleContext): Boolean {
    val atkLv = ctx.attacker.level
    val defLv = ctx.defender.level

    val atkStatus = ctx.attacker.statusValues
    val defStatus = ctx.defender.statusValues

    val atkDex = atkStatus.dexterity.toFloat()
    val defDex = defStatus.dexterity.toFloat()

    val atkAgi = atkStatus.agility.toFloat()
    val defAgi = defStatus.agility.toFloat()

    var crit = 0.02f
        + atkLv / defLv / 5
        + atkDex / defDex / 2
        + atkAgi / defAgi / 2

    crit *= ctx.damageVariables.criticalChanceMod
    crit = crit.clamp(0.01f, 0.95f)

    LOG.trace { "Crit chance: $crit" }

    return random.nextFloat() < crit
  }

  override fun isAttackPossible(ctx: BattleContext): Boolean {
    val isAttackInRange = isAttackInRange(ctx)

    return if (ctx.usedAttack.needsLineOfSight) {
      losService.hasLineOfSight(ctx.attacker.position, ctx.targetPosition()) && isAttackInRange
    } else {
      isAttackInRange
    }
  }

  /**
   * The roll is against *hitting*, and this answers the negation of it - a high hit rate has to mean
   * mostly landing. Ground has nothing to dodge with, so an aimed-at point is never missed.
   */
  protected fun isMiss(ctx: BattleContext): Boolean {
    return when (ctx) {
      is EntityBattleContext -> {
        val hitrate = (0.5f * ctx.attacker.derivedStatusValues.hitrate / ctx.defender.derivedStatusValues.flee)
          .clamp(0.05f, 1f)

        LOG.trace { "Hit chance: $hitrate" }

        random.nextFloat() >= hitrate
      }

      is GroundBattleContext -> false
    }
  }

  protected fun isAttackInRange(ctx: BattleContext): Boolean {
    return ctx.attacker.position.distance(ctx.targetPosition()) <= ctx.usedAttack.range
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
