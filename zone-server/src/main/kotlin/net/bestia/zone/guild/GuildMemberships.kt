package net.bestia.zone.guild

import net.bestia.zone.guild.persistence.GuildMember
import net.bestia.zone.guild.persistence.GuildMemberRepository
import net.bestia.zone.identity.ecs.GuildMembership
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** What a master's entity carries about its guild. */
@Component
class GuildMemberships(
  private val memberRepository: GuildMemberRepository,
) {

  /** Read from the database when the master comes into the world. */
  @Transactional(readOnly = true)
  fun of(masterId: Long): GuildMembership? {
    return memberRepository.findByMasterId(masterId)?.let { of(it) }
  }

  /** Copies the skill levels and the roster, so the component stays valid once the transaction has ended. */
  fun of(member: GuildMember): GuildMembership {
    return GuildMembership(
      guildId = member.guild.id,
      taxPercent = member.rank.taxPercent,
      isLeader = member.isLeader,
      mayUseGuildSkills = GuildRankRules.mayUseGuildSkills(member),
      skillLevels = member.guild.skillLevels.toMap(),
      members = member.guild.members.mapTo(HashSet()) { GuildMembership.Member(it.master.account.id, it.master.id) },
    )
  }
}
