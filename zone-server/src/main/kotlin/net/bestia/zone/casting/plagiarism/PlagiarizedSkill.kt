package net.bestia.zone.casting.plagiarism

import net.bestia.zone.ecs.core.Component

/** The spell a master copied with Plagiarism, to cast once at [level]. Lives as long as the master stays in the world. */
class PlagiarizedSkill(
  val skillId: Long,
  val level: Int,
) : Component
