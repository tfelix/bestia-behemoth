package net.bestia.zone.battle.attack

import net.bestia.zone.BestiaException

class SkillAlreadyLearnedException(skillId: Long) : BestiaException(
  code = "SKILL_ALREADY_LEARNED",
  message = "Skill $skillId is already learned"
)
