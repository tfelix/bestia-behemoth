package net.bestia.zone.battle.skill

import net.bestia.zone.BestiaException

class NoSkillScriptException : BestiaException(
  "NO_SKILL_SCRIPT",
  "No script was attached to the battle context"
)
