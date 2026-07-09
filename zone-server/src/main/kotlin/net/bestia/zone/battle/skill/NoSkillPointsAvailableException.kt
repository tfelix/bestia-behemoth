package net.bestia.zone.battle.skill

import net.bestia.zone.BestiaException

class NoSkillPointsAvailableException(masterId: Long) : BestiaException(
  code = "NO_SKILL_POINTS_AVAILABLE",
  message = "Master $masterId has no skill points available to spend"
)
