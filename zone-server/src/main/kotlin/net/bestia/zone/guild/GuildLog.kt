package net.bestia.zone.guild

import net.bestia.zone.guild.persistence.Guild
import net.bestia.zone.guild.persistence.GuildLogEntry
import net.bestia.zone.guild.persistence.GuildLogEntryRepository
import net.bestia.zone.guild.persistence.GuildLogKind
import net.bestia.zone.guild.persistence.GuildMemberRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** What happened in a guild, written inside the transaction that made it happen, so a rollback drops it too. */
@Component
class GuildLog(
  private val entryRepository: GuildLogEntryRepository,
  private val memberRepository: GuildMemberRepository,
) {

  /** Part of the caller's transaction; a guild keeps only its newest [KEPT] entries. */
  fun record(guild: Guild, kind: GuildLogKind, actorName: String, targetName: String = "", detail: String = "") {
    entryRepository.save(GuildLogEntry(guild, kind, actorName, targetName, detail))

    val oldestKeptId = entryRepository.findNewestIds(guild.id, PageRequest.of(KEPT - 1, 1)).firstOrNull() ?: return
    entryRepository.deleteOlderThan(guild.id, oldestKeptId)
  }

  /** Part of the caller's transaction, before the guild row itself goes. */
  fun forget(guildId: Long) {
    entryRepository.deleteByGuildId(guildId)
  }

  /** The newest entries of the guild [masterId] is in, newest first. */
  @Transactional(readOnly = true)
  fun newestFor(masterId: Long): GuildLogSMSG {
    val guildId = memberRepository.findGuildIdOfMaster(masterId) ?: throw NotInGuildException()
    val entries = entryRepository.findNewest(guildId, PageRequest.of(0, SENT))

    return GuildLogSMSG(entries.map { entry ->
      GuildLogSMSG.Entry(
        kind = entry.kind,
        atEpochMillis = entry.at.toEpochMilli(),
        actorName = entry.actorName,
        targetName = entry.targetName,
        detail = entry.detail,
      )
    })
  }

  companion object {
    const val KEPT = 200
    const val SENT = 50
  }
}
