package net.bestia.zone.guild

import jakarta.annotation.PreDestroy
import net.bestia.zone.persistence.AsyncJobExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Taxed EXP waiting to be written to its guild.
 *
 * Kills land on the tick, many a second; a guild row write per kill would put one row lock per kill on the
 * database. So the tax is summed here per guild and member and written once per [GuildConfig.expFlushSeconds],
 * one transaction per guild. A crash loses what was gathered since the last flush.
 */
@Component
class GuildExpLedger(
  private val jobs: AsyncJobExecutor,
  private val writer: GuildExpWriter,
  private val publisher: GuildInfoPublisher,
) {

  private data class Key(val guildId: Long, val masterId: Long)

  /** Never shrinks but on [discard]: a key per member who has paid, and removing one could lose a racing add. */
  private val pending = ConcurrentHashMap<Key, AtomicLong>()

  /** From the tick. Never blocks. */
  fun add(guildId: Long, masterId: Long, amount: Long) {
    pending.computeIfAbsent(Key(guildId, masterId)) { AtomicLong() }.addAndGet(amount)
  }

  /** Forgets what a disbanded guild had gathered. */
  fun discard(guildId: Long) {
    pending.keys.removeIf { it.guildId == guildId }
  }

  @Scheduled(fixedDelayString = "\${guild.exp-flush-seconds:30}", timeUnit = TimeUnit.SECONDS)
  fun flush() {
    drain().forEach { (guildId, contributions) ->
      jobs.submit(GuildInfoPublisher.keyOf(guildId)) {
        if (writer.apply(guildId, contributions)) {
          publisher.publishNow(guildId)
        }
      }
    }
  }

  /** Best effort: whether the database is still there when this runs is up to the order Spring shuts down in. */
  @PreDestroy
  fun flushOnShutdown() {
    drain().forEach { (guildId, contributions) -> writer.apply(guildId, contributions) }
  }

  /** What has been gathered, by guild and member, zeroed as it is read so an add racing the drain is kept. */
  private fun drain(): Map<Long, Map<Long, Long>> {
    val byGuild = HashMap<Long, MutableMap<Long, Long>>()

    for ((key, amount) in pending) {
      val taken = amount.getAndSet(0)
      if (taken > 0) {
        byGuild.getOrPut(key.guildId) { HashMap() }[key.masterId] = taken
      }
    }

    return byGuild
  }
}
