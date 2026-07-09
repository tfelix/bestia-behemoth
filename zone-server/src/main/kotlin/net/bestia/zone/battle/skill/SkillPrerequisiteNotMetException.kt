package net.bestia.zone.battle.skill

import net.bestia.zone.BestiaException

class SkillPrerequisiteNotMetException(
  skillId: Long,
  prerequisiteSkillId: Long,
  requiredLevel: Int,
  currentLevel: Int
) : BestiaException(
  code = "SKILL_PREREQUISITE_NOT_MET",
  message = "Skill $skillId requires prerequisite $prerequisiteSkillId at level " +
    "$requiredLevel, but it is currently at level $currentLevel"
)
