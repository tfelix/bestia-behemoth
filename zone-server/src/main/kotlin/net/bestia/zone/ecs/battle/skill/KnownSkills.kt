package net.bestia.zone.ecs.battle.skill

import net.bestia.zone.ecs.core.Component

/**
 * This contains the known skill ids and is mostly important for NPC controlled bestia where the AI needs to access
 * the known skills without doing a DB lookup for attack calculations.
 */
class KnownSkills(
  private var availableSkills: MutableMap<Long, Int>
) : Component {

  fun knowsSkill(skillId: Long, skillLevel: Int = 0): Boolean {
    assert(skillLevel >= 0)

    return availableSkills.getOrDefault(skillId, 0) >= skillLevel
  }

  /**
   * What level this entity has in [skillId], or zero if it does not know it.
   *
   * [knowsSkill] answers a threshold, which is what a combat check wants. A *passive* whose effect scales with
   * its level needs the number itself - `WEATHER_SENSE` buys five minutes of foresight per level - and a caller
   * that had to binary-search `knowsSkill` to find it would be absurd.
   */
  fun levelOf(skillId: Long): Int = availableSkills.getOrDefault(skillId, 0)

  fun learnOrUpdate(skillId: Long, skillLevel: Int = 1) {
    availableSkills[skillId] = skillLevel
  }
}