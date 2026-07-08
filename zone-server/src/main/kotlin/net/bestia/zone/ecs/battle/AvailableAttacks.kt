package net.bestia.zone.ecs.battle

import net.bestia.zone.ecs.core.Component

class AvailableAttacks(
  /**
   * This encodes the available attack ID and its attack level. Combines both the entity's
   * fixed (species/tree, level-gated) attacks and its custom-learned ones - it is the
   * server-side source of truth for attack validation, never synced to the client directly
   * (the client already knows the fixed part; see [net.bestia.zone.ecs.battle.LearnedSkills]
   * for the part that does get synced).
   */
  var availableAttackIds: MutableMap<Long, Int>
) : Component {

  fun knowsAttack(usedAttackId: Long, usedSkillLevel: Int): Boolean {
    val knownSkillLevel = availableAttackIds[usedAttackId] ?: 0

    return knownSkillLevel >= usedSkillLevel
  }

  fun learnOrUpdate(attackId: Long, level: Int) {
    availableAttackIds[attackId] = level
  }
}