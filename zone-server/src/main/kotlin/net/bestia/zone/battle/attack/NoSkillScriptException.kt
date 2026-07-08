package net.bestia.zone.battle.attack

import net.bestia.zone.BestiaException

class NoSkillScriptException : BestiaException(
  "NO_SKILL_SCRIPT",
  "No script was attached to the battle context"
)
