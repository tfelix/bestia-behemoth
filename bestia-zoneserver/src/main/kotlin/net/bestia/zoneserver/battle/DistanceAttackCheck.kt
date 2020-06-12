package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.PositionComponent

private val LOG = KotlinLogging.logger { }

class DistanceAttackCheck(
    private val battleCtx: EntityBattleContext
) : AttackCheck() {
  /**
   * Checks if a given attack is in range for a target position. It is
   * important to ask the attached entity scripts as these can alter the
   * effective range.
   *
   * @return TRUE if the attack is in range. FALSE otherwise.
   */
  override fun checkAttackCondition(): Boolean {
    val atkPosition = getPosition(battleCtx.attacker)
    val defPosition = getPosition(battleCtx.defender)

    val effectiveRange = getEffectiveSkillRange(battleCtx.usedAttack, battleCtx.attacker)

    LOG.trace("Effective attack range: {}", effectiveRange)

    return effectiveRange >= atkPosition.getDistance(defPosition)
  }

  private fun getPosition(e: Entity): Vec3 {
    return e.getComponent(PositionComponent::class.java).position
  }

  /**
   * Calculates the effective range of the attack. A skill range can be
   * altered by an equipment or a buff for example.
   */
  private fun getEffectiveSkillRange(attack: BattleAttack, entity: Entity): Long {
    // TODO Take status modifications into account.
    return attack.range
  }
}