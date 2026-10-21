package net.bestia.zone.guild

import net.bestia.zone.identity.GroupMember
import net.bestia.zone.guild.persistence.GuildMemberRepository
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Who a guild-wide line reaches, such as guild chat: every member, the sender included, played or not. */
@Component
class GuildAudience(
  private val memberRepository: GuildMemberRepository,
  private val sessions: ConnectionInfoService,
) {

  /** The members of the guild of the master [accountId] is playing, or null when it is in none. Off the tick: it queries. */
  @Transactional(readOnly = true)
  fun of(accountId: AccountId): List<GroupMember>? {
    val masterId = sessions.activeMasterIdOf(accountId) ?: return null

    return ofMaster(masterId)
  }

  /** The members of [masterId]'s guild, or null when it is in none, whether anybody is playing it or not. */
  @Transactional(readOnly = true)
  fun ofMaster(masterId: Long): List<GroupMember>? {
    val guildId = memberRepository.findGuildIdOfMaster(masterId) ?: return null

    return memberRepository.findRefsByGuildId(guildId).map { GroupMember(it.accountId, it.masterId) }
  }
}
