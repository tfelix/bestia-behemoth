package net.bestia.api.auth

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import net.bestia.api.bus.ZoneClient
import net.bestia.api.metrics.AuthMetrics
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Whose game connection a ticket belongs to, asked of the zone and remembered briefly. A ticket dies with its
 * socket; the account's `Presence` event drops it here at once, and the TTL bounds how long a missed event can
 * keep a dead ticket working.
 */
@Component
class TicketOwners(
  private val zone: ZoneClient,
  private val metrics: AuthMetrics,
  registry: MeterRegistry,
) {

  private val owners: Cache<String, Long> = Caffeine.newBuilder()
    .expireAfterWrite(TIME_TO_LIVE)
    .maximumSize(MAX_TICKETS)
    .recordStats()
    .build<String, Long>()
    .also { CaffeineCacheMetrics.monitor(registry, it, "ticket_owners") }

  /** The ticket's account, or null when no live connection holds it. */
  fun accountFor(ticket: String): Long? {
    owners.getIfPresent(ticket)?.let {
      metrics.ticketLookup(AuthMetrics.TicketLookup.CACHE_HIT)
      return it
    }

    val reply = zone.ask(NO_ACCOUNT_YET, ZoneQuery.WhoseTicket(ticket))
    val owner = (reply.answer as? ZoneAnswer.TicketOwner)?.takeIf { reply.status == BusReply.Status.OK }
    if (owner == null) {
      metrics.ticketLookup(AuthMetrics.TicketLookup.ZONE_MISS)
      return null
    }

    metrics.ticketLookup(AuthMetrics.TicketLookup.ZONE_HIT)
    owners.put(ticket, owner.accountId)
    return owner.accountId
  }

  fun forgetAccount(accountId: Long) {
    owners.asMap().values.removeIf { it == accountId }
  }

  private companion object {
    val TIME_TO_LIVE: Duration = Duration.ofSeconds(30)
    const val MAX_TICKETS = 50_000L

    /** The one question asked before anybody is known; the zone ignores the account on it. */
    const val NO_ACCOUNT_YET = 0L
  }
}
