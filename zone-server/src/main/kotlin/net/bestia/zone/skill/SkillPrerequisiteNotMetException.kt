package net.bestia.zone.skill

import net.bestia.zone.BestiaException

class SkillPrerequisiteNotMetException(
  skillIdentifier: String,
  prerequisiteSkillIdentifier: String,
  requiredLevel: Int,
  currentLevel: Int
) : BestiaException(
  code = "SKILL_PREREQUISITE_NOT_MET",
  message = "Skill $skillIdentifier requires prerequisite $prerequisiteSkillIdentifier at level " +
    "$requiredLevel, but it is currently at level $currentLevel"
)
