package net.bestia.zone.ecs.battle

import net.bestia.zone.ecs.Component

class AvailableAttacks(
  /**
   * This encodes the available attack ID and its attack level.
   */
  var availableAttackIds: MutableMap<Long, Int>
) : Component {

  fun knowsAttack(usedAttackId: Long, usedSkillLevel: Int): Boolean {
    val knownSkillLevel = availableAttackIds[usedAttackId] ?: 0

    return knownSkillLevel >= usedSkillLevel
  }
}