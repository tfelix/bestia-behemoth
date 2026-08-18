package net.bestia.zone.skill

import net.bestia.zone.BestiaException

class SkillSubTreeNotUnlockedException(
  subTree: String,
  tree: String,
  requiredPoints: Int,
  currentPoints: Int
) : BestiaException(
  code = "SKILL_SUB_TREE_NOT_UNLOCKED",
  message = "Sub-tree $subTree requires $requiredPoints points spent in tree $tree, but only " +
    "$currentPoints are spent"
)
