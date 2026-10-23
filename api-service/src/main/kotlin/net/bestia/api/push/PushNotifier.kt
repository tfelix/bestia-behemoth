package net.bestia.api.push

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.api.bus.BusClientProperties
import net.bestia.api.live.LiveSessions
import net.bestia.api.metrics.PushMetrics
import net.bestia.bus.BusKeys
import net.bestia.bus.Directory
import net.bestia.bus.StreamEntry
import net.bestia.bus.contract.ZoneEvent
import net.bestia.bus.valkey.ValkeyStreamReader
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Turns zone events into notifications for accounts that cannot see them otherwise: not in the world, and with no
 * socket open to this service. Works the stream as one consumer group, so each event is notified once however many
 * instances run.
 *
 * Each account gets at most `push.per-account-per-hour`; past that, the rest of the hour's events are dropped,
 * since a game with this many timers would otherwise ring a phone all day.
 */
@Component
class PushNotifier(
  private val events: ValkeyStreamReader<ZoneEvent>,
  private val directory: Directory,
  private val sessions: LiveSessions,
  private val subscriptions: PushSubscriptionRepository,
  private val preferences: PushPreferenceRepository,
  private val sender: WebPushSender,
  private val properties: PushProperties,
  private val bus: BusClientProperties,
  private val metrics: PushMetrics,
) {

  private val sentThisHour = ConcurrentHashMap<Long, AtomicInteger>()
  private val mapper = jacksonObjectMapper()

  @EventListener(ApplicationReadyEvent::class)
  fun start() {
    events.consume(BusKeys.WEBPUSH_GROUP, bus.instanceId, ::handle)
  }

  fun handle(streamed: StreamEntry<ZoneEvent>) {
    val event = streamed.payload
    val notification = Notification.of(event) ?: return
    if (!wanted(event)) {
      metrics.dropped(PushMetrics.Dropped.UNWANTED)
      return
    }
    if (sessions.isConnected(event.accountId) || directory.nodeOf(event.accountId) != null) {
      metrics.dropped(PushMetrics.Dropped.ONLINE)
      return
    }
    if (sentThisHour.computeIfAbsent(event.accountId) { AtomicInteger() }.incrementAndGet() > properties.perAccountPerHour) {
      metrics.dropped(PushMetrics.Dropped.RATE_LIMIT)
      return
    }

    deliver(event.accountId, notification)
  }

  fun sendTest(accountId: Long) {
    deliver(accountId, Notification("Bestia", "Notifications reach this device.", "/", "test"))
  }

  private fun wanted(event: ZoneEvent): Boolean {
    val preference = preferences.findById(event.accountId).orElse(null) ?: PushPreference.defaults(event.accountId)
    if (preference.isQuietAt(ZonedDateTime.now())) {
      return false
    }

    return when (event) {
      is ZoneEvent.HomeFell -> preference.homeFell
      is ZoneEvent.BestiaDiedAway -> preference.bestiaDied
      is ZoneEvent.ConstructionDone -> preference.constructionDone
      else -> false
    }
  }

  private fun deliver(accountId: Long, notification: Notification) {
    val payload = mapper.writeValueAsBytes(notification)

    for (subscription in subscriptions.findAllByAccountId(accountId)) {
      val outcome = sender.send(subscription, payload)
      metrics.outcome(outcome)
      when (outcome) {
        WebPushSender.Outcome.DELIVERED -> {
          subscription.lastOkAt = LocalDateTime.now()
          subscriptions.save(subscription)
        }

        WebPushSender.Outcome.GONE -> {
          LOG.info { "Dropping push subscription ${subscription.id} of account $accountId: the push service forgot it" }
          subscriptions.delete(subscription)
        }

        WebPushSender.Outcome.FAILED -> {}
      }
    }
  }

  @Scheduled(cron = "0 0 * * * *")
  fun newHour() {
    sentThisHour.clear()
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
