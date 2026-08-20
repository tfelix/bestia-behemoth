package net.bestia.zone.skill

import net.bestia.zone.BestiaException

class SkillCatalogMismatchException(problems: List<String>) : BestiaException(
  code = "SKILL_CATALOG_MISMATCH",
  message = "SkillId and skills.yml are out of sync:\n" + problems.joinToString("\n") { "  - $it" }
)
