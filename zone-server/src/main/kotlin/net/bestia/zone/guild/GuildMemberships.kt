package net.bestia.zone.guild

import net.bestia.zone.guild.persistence.GuildMemberRepository
import net.bestia.zone.identity.ecs.GuildMembership
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** What a master's entity carries about its guild, read from the database when the master comes into the world. */
@Component
class GuildMemberships(
  private val memberRepository: GuildMemberRepository,
) {

  @Transactional(readOnly = true)
  fun of(masterId: Long): GuildMembership? {
    return memberRepository.findMembershipOfMaster(masterId)?.let { GuildMembership(it.guildId, it.taxPercent) }
  }
}
