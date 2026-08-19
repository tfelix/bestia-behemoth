package net.bestia.zone.battle.skill

import net.bestia.zone.BestiaException

/**
 * The skill has no [SkillStrategy] behind it, so it cannot be cast. Either it is a passive, or its script
 * is not implemented yet - `SkillScriptBootValidator` lists the latter at boot.
 */
class NoSkillScriptException(identifier: String) : BestiaException(
  "NO_SKILL_SCRIPT",
  "Skill $identifier has no script implementation and cannot be cast"
)
