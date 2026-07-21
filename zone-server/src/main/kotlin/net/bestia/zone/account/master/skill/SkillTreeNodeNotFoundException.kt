package net.bestia.zone.account.master.skill

import net.bestia.zone.BestiaException

class SkillTreeNodeNotFoundException(skillId: Long) : BestiaException(
  code = "SKILL_TREE_NODE_NOT_FOUND",
  message = "Skill $skillId is not part of the master skill tree"
)