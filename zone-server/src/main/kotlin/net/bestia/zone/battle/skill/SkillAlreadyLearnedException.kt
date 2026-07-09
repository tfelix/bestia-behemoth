package net.bestia.zone.battle.skill

import net.bestia.zone.BestiaException

class SkillAlreadyLearnedException(skillId: Long) : BestiaException(
  code = "SKILL_ALREADY_LEARNED",
  message = "Skill $skillId is already learned"
)
