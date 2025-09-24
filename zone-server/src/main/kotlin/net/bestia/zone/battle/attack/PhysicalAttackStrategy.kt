package net.bestia.zone.battle.attack

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.*
import net.bestia.zone.util.clamp
import java.util.*

abstract class PhysicalAttackStrategy(
  private val losService: LineOfSightService,
  private val random: Random
) : AttackStrategy {
  protected fun isCriticalHit(ctx: BattleContext): Boolean {
    return when (ctx) {
      is EntityBattleContext -> isCriticalHit(ctx)
      is GroundBattleContext -> isCriticalHit(ctx)
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

    val dmgVars = ctx.damageVariables
    crit *= dmgVars.criticalChanceMod

    crit = crit.clamp(0.01f, 0.95f)

    LOG.trace { "Crit chance: $crit" }

    return random.nextFloat() < crit
  }

  private fun isCriticalHit(ctx: GroundBattleContext): Boolean {
    return false
  }


  override fun isAttackPossible(ctx: BattleContext): Boolean {
    val isAttackInRange = isAttackInRange(ctx)

    return if (ctx.usedAttack.needsLineOfSight) {
      val targetPos = ctx.targetPosition()
      val hasLos = losService.hasLineOfSight(ctx.attacker.position, targetPos)

      hasLos && isAttackInRange
    } else {
      isAttackInRange
    }
  }

  protected fun isMiss(ctx: BattleContext): Boolean {
    return when (ctx) {
      is EntityBattleContext -> {
        var hitrate = 0.5f * ctx.attacker.derivedStatusValues.hitrate / ctx.defender.derivedStatusValues.flee

        hitrate = hitrate.clamp(0.05f, 1f)

        LOG.trace { "Hit Chance: $hitrate" }

        return random.nextFloat() < hitrate
      }

      is GroundBattleContext -> false
    }
  }

  protected fun isAttackInRange(
    ctx: BattleContext
  ): Boolean {
    val attackerPos = ctx.attacker.position
    val targetPos = ctx.targetPosition()
    val atkRange = ctx.usedAttack.range

    return attackerPos.distance(targetPos) <= atkRange
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}