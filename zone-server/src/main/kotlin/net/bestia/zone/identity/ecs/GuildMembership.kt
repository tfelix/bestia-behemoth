package net.bestia.zone.identity.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.AccountId

/**
 * Marks a master entity as being in a guild, with what the tick needs to know about it: the tax its rank pays,
 * whether it leads or may cast guild skills, the guild's skill levels, so the guild skills work with a plain
 * component read, and the roster, which says who hears its [net.bestia.zone.guild.ecs.GuildMemberBeacon].
 *
 * Kept up to date by `GuildService` once a change has committed - a member joining or leaving, a new rank, a
 * rank's tax, a skill point spent - and added by `MasterEntitySpawner` when the master logs in.
 */
data class GuildMembership(
  val guildId: Long,
  val taxPercent: Int,
  val isLeader: Boolean = false,
  val mayUseGuildSkills: Boolean = false,
  /** By `skills.yml` identifier; a skill the guild never learned has no entry. */
  val skillLevels: Map<String, Int> = emptyMap(),
  val members: Set<Member> = emptySet(),
) : Component {

  /** Keyed by master as well as account, as in [PartyMembership]: an account may play another of its masters. */
  data class Member(
    val accountId: AccountId,
    val masterId: Long,
  )

  fun skillLevel(identifier: String): Int {
    return skillLevels[identifier] ?: 0
  }
}
