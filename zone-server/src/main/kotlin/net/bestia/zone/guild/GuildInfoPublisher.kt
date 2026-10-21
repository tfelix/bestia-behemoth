package net.bestia.zone.guild

import net.bestia.zone.guild.persistence.GuildMemberRef
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * The one way guild state reaches its members: every member being played gets the whole guild whenever any of
 * it changes. Built off the tick on a job keyed by the guild - requests arriving while one is queued fold into
 * it, and a departure queued behind it cannot be overtaken by it. The EXP flush runs on the same key.
 *
 * Safe to call from any thread, except [sendTo], which answers inline and belongs on the IO lane.
 */
@Component
class GuildInfoPublisher(
  private val reader: GuildInfoReader,
  private val sessions: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor,
  private val jobs: AsyncJobExecutor,
) {

  /** Guilds with a push queued and not yet started. */
  private val queued: MutableSet<Long> = ConcurrentHashMap.newKeySet()

  fun request(guildId: Long) {
    if (!queued.add(guildId)) {
      return
    }

    jobs.submit(keyOf(guildId)) {
      // Cleared as the job starts, not when it ends: a change made while it is built needs a new push.
      queued.remove(guildId)
      publishNow(guildId)
    }
  }

  /** [request] for whatever guild [masterId] is in, if any. */
  fun requestForMaster(masterId: Long) {
    jobs.submit(MASTER_KEY_PREFIX + masterId) {
      reader.guildIdOfMaster(masterId)?.let { request(it) }
    }
  }

  /** Answers [accountId] at once with its guild, or an empty one. IO lane. */
  fun sendTo(accountId: AccountId) {
    val masterId = sessions.activeMasterIdOf(accountId) ?: return
    val snapshot = reader.guildIdOfMaster(masterId)?.let { reader.read(it) }

    outMessageProcessor.sendToPlayer(
      accountId,
      snapshot?.info?.copy(ownMasterId = masterId) ?: GuildInfoSMSG.none(masterId)
    )
  }

  /** [member] is out of [guildId], which goes on: tell it why, and give the rest the guild without it. */
  fun left(guildId: Long, member: GuildMemberRef, reason: GuildLeftSMSG.Reason) {
    jobs.submit(keyOf(guildId)) {
      if (sessions.isPlaying(member.accountId, member.masterId)) {
        outMessageProcessor.sendToPlayer(member.accountId, GuildLeftSMSG(guildId, reason))
      }
      publishNow(guildId)
    }
  }

  /** [guildId] ended: tell each of its former [members] being played. */
  fun disbanded(guildId: Long, members: List<GuildMemberRef>) {
    jobs.submit(keyOf(guildId)) {
      val message = GuildLeftSMSG(guildId, GuildLeftSMSG.Reason.DISBANDED)
      members
        .filter { sessions.isPlaying(it.accountId, it.masterId) }
        .forEach { outMessageProcessor.sendToPlayer(it.accountId, message) }
    }
  }

  /** Sends the guild as it stands to every member being played. On the caller's thread: [keyOf] or the IO lane. */
  fun publishNow(guildId: Long) {
    val snapshot = reader.read(guildId) ?: return

    snapshot.members
      .filter { sessions.isPlaying(it.accountId, it.masterId) }
      .forEach { outMessageProcessor.sendToPlayer(it.accountId, snapshot.info.copy(ownMasterId = it.masterId)) }
  }

  companion object {
    private const val GUILD_KEY_PREFIX = "guild:"
    private const val MASTER_KEY_PREFIX = "guild-of-master:"

    /** The job key everything touching one guild's rows runs on, so it all happens in order. */
    fun keyOf(guildId: Long): String = GUILD_KEY_PREFIX + guildId
  }
}
