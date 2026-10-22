package net.bestia.zone.guild.skill

/** One skill of the guild skill tree: how far a guild may raise it, and what it needs first. */
data class GuildSkillNode(
  val skillId: Long,
  /** The skill's `skills.yml` identifier, which is also the key a guild's levels are stored under. */
  val identifier: String,
  val maxLevel: Int,
  val prerequisites: List<Prerequisite> = emptyList(),
) {
  data class Prerequisite(val skillId: Long, val level: Int)
}
