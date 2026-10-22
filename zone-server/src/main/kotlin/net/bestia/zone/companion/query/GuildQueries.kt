package net.bestia.zone.companion.query

import net.bestia.bus.contract.ZoneAnswer
import net.bestia.zone.guild.GuildInfoReader
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** A master's guild for the companion app, read as the guild window reads it. */
@Service
class GuildQueries(
  private val owned: OwnedMasters,
  private val reader: GuildInfoReader,
) {

  /** Null when [masterId] is not [accountId]'s. */
  @Transactional(readOnly = true)
  fun guild(accountId: Long, masterId: Long): ZoneAnswer.GuildInfo? {
    owned.find(accountId, masterId) ?: return null
    val info = reader.guildIdOfMaster(masterId)?.let { reader.read(it)?.info } ?: return ZoneAnswer.GuildInfo(null)

    val rankTitles = info.ranks.associate { it.rankId to it.name }
    val members = info.members.map {
      ZoneAnswer.GuildInfo.Member(
        masterId = it.masterId,
        name = it.masterName,
        level = it.level,
        rankTitle = rankTitles[it.rankId].orEmpty(),
        isLeader = it.isLeader,
        online = it.online,
        lastAreaName = it.lastAreaName,
        lastSeenEpochMillis = it.lastSeenEpochMillis,
      )
    }
    return ZoneAnswer.GuildInfo(
      ZoneAnswer.GuildInfo.Guild(info.name, info.level, info.motdTitle, info.motdBody, info.emblemHash, members)
    )
  }
}
