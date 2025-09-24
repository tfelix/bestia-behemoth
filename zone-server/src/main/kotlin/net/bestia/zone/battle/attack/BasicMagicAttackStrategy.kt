package net.bestia.zone.battle.attack

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.geometry.Vec3L

abstract class BasicMagicAttackStrategy(
  private val losService: LineOfSightService,
) : AttackStrategy {

  override fun isAttackPossible(ctx: BattleContext): Boolean {
    val targetPos = getTargetPosition(ctx)

    return if (ctx.usedAttack.needsLineOfSight) {
      val hasLos = losService.hasLineOfSight(ctx.attacker.position, targetPos)

      hasLos && isAttackInRange(ctx.attacker.position, targetPos, ctx.usedAttack.range)
    } else {
      isAttackInRange(ctx.attacker.position, targetPos, ctx.usedAttack.range)
    }
  }

  private fun getTargetPosition(ctx: BattleContext): Vec3L {
    return when (ctx) {
      is EntityBattleContext -> ctx.defender.position
      is GroundBattleContext -> ctx.targetPosition
    }
  }

  private fun isAttackInRange(
    attackerPos: Vec3L,
    targetPos: Vec3L,
    range: Long
  ): Boolean {
    return attackerPos.distance(targetPos) <= range
  }
}