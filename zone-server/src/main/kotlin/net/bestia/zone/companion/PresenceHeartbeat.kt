package net.bestia.zone.companion

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bus.Directory
import net.bestia.bus.EventPublisher
import net.bestia.bus.contract.ZoneEvent
import net.bestia.zone.bus.BusProperties
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Keeps this node's accounts in the directory and tells the stream who came and went. A sweep rather than a hook
 * on every session change: an entry has to be renewed anyway, and a change shows within one beat.
 */
@Component
class PresenceHeartbeat(
  private val connections: ConnectionInfoService,
  private val directory: Directory,
  private val events: EventPublisher,
  private val properties: BusProperties,
) {

  private var previous: Map<AccountId, Long> = emptyMap()

  @Scheduled(fixedDelay = BEAT_MILLIS, initialDelay = BEAT_MILLIS)
  fun beat() {
    val current = connections.activeMasters()

    try {
      directory.register(current.keys, properties.nodeId, properties.directoryTtlSeconds)
      (previous.keys - current.keys).forEach { directory.remove(it) }
    } catch (e: Exception) {
      // The entries lapse on their own; the next beat writes them again.
      LOG.warn { "Directory not updated: ${e.message}" }
      return
    }

    for ((accountId, masterId) in current) {
      if (previous[accountId] != masterId) {
        events.publish(ZoneEvent.Presence(accountId, masterId, inWorld = true))
      }
    }
    for (accountId in previous.keys - current.keys) {
      events.publish(ZoneEvent.Presence(accountId, masterId = null, inWorld = false))
    }
    previous = current
  }

  private companion object {
    const val BEAT_MILLIS = 10_000L
    val LOG = KotlinLogging.logger { }
  }
}
