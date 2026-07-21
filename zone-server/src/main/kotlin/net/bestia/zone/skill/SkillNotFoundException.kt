package net.bestia.zone.skill

import net.bestia.zone.BestiaException

class SkillNotFoundException(id: Long) : BestiaException(
  code = "SKILL_NOT_FOUND",
  message = "Skill $id not found"
)
