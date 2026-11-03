package net.bestia.zone.skill.persistence

/** An item a cast uses up, by identifier as authored; [itemId] is resolved by the importer. */
data class SkillReagent(
  val item: String = "",
  val amount: Int = 1,
  val itemId: Long = 0,
)
