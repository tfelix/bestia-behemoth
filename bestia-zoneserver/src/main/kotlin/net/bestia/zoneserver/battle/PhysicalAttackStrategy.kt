package net.bestia.zoneserver.battle

import mu.KotlinLogging
import java.util.*

private val LOG = KotlinLogging.logger { }

abstract class PhysicalAttackStrategy(
    private val random: Random
) : AttackStrategy {
  override fun isCriticalHit(battleCtx: BattleContext): Boolean {
    val dmgVars = battleCtx.damageVariables

    val atkLv = battleCtx.attackerLevel
    val defLv = battleCtx.defenderLevel

    val atkStatus = battleCtx.attackerStatusPoints
    val defStatus = battleCtx.defenderStatusPoints

    val atkDex = atkStatus.dexterity.toFloat()
    val defDex = defStatus.dexterity.toFloat()

    val atkAgi = atkStatus.agility.toFloat()
    val defAgi = defStatus.agility.toFloat()

    var crit = (0.02f + (atkLv / defLv / 5)
        + atkDex / defDex / 2
        + atkAgi / defAgi / 2)

    crit *= dmgVars.criticalChanceMod

    crit = crit.clamp(0.01f, 0.95f)

    LOG.trace { "Crit chance: $crit" }

    return random.nextFloat() < crit
  }

  override fun doesAttackHit(battleCtx: BattleContext): Boolean {
    val atkStatBased = battleCtx.attackerStatusBased
    val defStatBased = battleCtx.defenderStatusBased

    var hitrate = 0.5f * atkStatBased.hitrate / defStatBased.dodge

    if (battleCtx.damageVariables.isCriticalHit) {
      hitrate *= 3f
    }

    hitrate = hitrate.clamp(0.05f, 1f)

    LOG.trace { "Hit Chance: $hitrate" }

    return random.nextFloat() < hitrate
  }
}