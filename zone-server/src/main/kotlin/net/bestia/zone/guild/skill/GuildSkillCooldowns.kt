package net.bestia.zone.guild.skill

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * When each guild may cast each of its active skills again. One cast blocks the skill for every member allowed
 * to cast it. In memory only: a restart lets every guild skill be cast at once.
 */
@Component
class GuildSkillCooldowns {

  private data class Key(val guildId: Long, val skillId: Long)

  private val readyAt = ConcurrentHashMap<Key, Long>()

  fun remainingMillis(guildId: Long, skillId: Long, now: Long = System.currentTimeMillis()): Long {
    val ready = readyAt[Key(guildId, skillId)] ?: return 0

    return (ready - now).coerceAtLeast(0)
  }

  /** Starts the cooldown unless it is still running. @return false when it was, and the cast must not land */
  fun tryStart(guildId: Long, skillId: Long, cooldownMillis: Long, now: Long = System.currentTimeMillis()): Boolean {
    var started = false
    readyAt.compute(Key(guildId, skillId)) { _, ready ->
      if (ready != null && ready > now) {
        ready
      } else {
        started = true
        now + cooldownMillis
      }
    }

    return started
  }

  fun forget(guildId: Long) {
    readyAt.keys.removeIf { it.guildId == guildId }
  }
}
