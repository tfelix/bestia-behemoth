package net.bestia.zone.identity.ecs

import net.bestia.zone.ecs.core.Component

/**
 * Marks a master entity as being in a guild, with what the tick needs to know about it: the tax its rank pays,
 * whether it leads or may cast guild skills, and the guild's skill levels, so the guild skills work with a plain
 * component read.
 *
 * Kept up to date by `GuildService` once a change has committed - joining, leaving, a new rank, a rank's tax, a
 * skill point spent - and added by `MasterEntitySpawner` when the master logs in.
 */
data class GuildMembership(
  val guildId: Long,
  val taxPercent: Int,
  val isLeader: Boolean = false,
  val mayUseGuildSkills: Boolean = false,
  /** By `skills.yml` identifier; a skill the guild never learned has no entry. */
  val skillLevels: Map<String, Int> = emptyMap(),
) : Component {

  fun skillLevel(identifier: String): Int {
    return skillLevels[identifier] ?: 0
  }
}
