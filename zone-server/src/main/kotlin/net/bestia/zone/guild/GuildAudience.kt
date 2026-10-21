package net.bestia.zone.guild

import net.bestia.zone.guild.persistence.GuildMemberRepository
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Who a guild-wide line reaches, such as guild chat: every member being played, the sender included. */
@Component
class GuildAudience(
  private val memberRepository: GuildMemberRepository,
  private val sessions: ConnectionInfoService,
) {

  /** The accounts playing a member of [accountId]'s guild, or null when it is in none. Off the tick: it queries. */
  @Transactional(readOnly = true)
  fun of(accountId: AccountId): Set<AccountId>? {
    val masterId = sessions.activeMasterIdOf(accountId) ?: return null
    val guildId = memberRepository.findGuildIdOfMaster(masterId) ?: return null

    return memberRepository.findRefsByGuildId(guildId)
      .filter { sessions.isPlaying(it.accountId, it.masterId) }
      .mapTo(HashSet()) { it.accountId }
  }
}
