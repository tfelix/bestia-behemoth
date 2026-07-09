package net.bestia.zone.ecs.battle

import net.bestia.zone.ecs.core.Component

/**
 * This contains the known skill ids and is mostly important for NPC controlled bestia where the AI needs to access
 * the known skills without doing a DB lookup for attack calculations.
 */
class AvailableSkills(
  private var availableSkills: MutableMap<Long, Int>
) : Component {

  constructor(
    skillIds: Set<Long> = emptySet()
  ) : this(skillIds.associateWith { 1 }.toMutableMap())

  fun knowsSkill(skillId: Long, skillLevel: Int = 0): Boolean {
    assert(skillLevel >= 0)

    return availableSkills.getOrDefault(skillId, 0) >= skillLevel
  }

  fun learnOrUpdate(skillId: Long, skillLevel: Int = 1) {
    availableSkills[skillId] = skillLevel
  }
}