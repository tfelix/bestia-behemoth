package net.bestia.zone.battle.attack

import net.bestia.zone.BestiaException

class SkillMaxLevelReachedException(skillId: Long, maxLevel: Int) : BestiaException(
  code = "SKILL_MAX_LEVEL_REACHED",
  message = "Skill $skillId is already at its max level $maxLevel"
)
