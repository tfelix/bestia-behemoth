package net.bestia.zone.skill

import net.bestia.zone.BestiaException

class BasicSkillTooLowForTreeException(
  masterId: Long,
  tree: String,
  requiredLevel: Int,
  currentLevel: Int
) : BestiaException(
  code = "BASIC_SKILL_TOO_LOW_FOR_TREE",
  message = "Tree $tree requires Basic Skill $requiredLevel, but master $masterId has it at $currentLevel"
)
