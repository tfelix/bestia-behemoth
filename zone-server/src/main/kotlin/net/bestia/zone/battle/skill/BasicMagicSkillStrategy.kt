package net.bestia.zone.battle.skill

import net.bestia.zone.battle.LineOfSightService

/**
 * The range and line-of-sight gate almost every cast skill wants, so a script only has to say what it
 * does and not re-derive where it reaches.
 *
 * Both numbers come from the catalogue via [BattleAttack], which is what keeps the reach the client
 * enforces while aiming and the reach the server checks the same value.
 */
abstract class BasicMagicSkillStrategy(
  private val losService: LineOfSightService,
) : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val attack = ctx.battle.usedAttack
    val attackerPos = ctx.battle.attacker.position
    val targetPos = ctx.aimedAt

    if (attackerPos.distance(targetPos) > attack.range) {
      return false
    }

    return !attack.needsLineOfSight || losService.hasLineOfSight(attackerPos, targetPos)
  }
}
