package net.bestia.zone.auction

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Trade Agreements offered and not answered yet. An offer lapses after a minute. */
@Component
class TradeOffers(
  private val clock: Clock = Clock.systemUTC(),
) {

  /** [fromLevel] is the offerer's Trade Agreement level, which caps their partners. */
  data class Offer(
    val from: TradeLinks.Partner,
    val fromAccountId: Long,
    val toMasterId: Long,
    val fromLevel: Int,
    val expiresAt: Instant,
  )

  private val offers = ConcurrentHashMap<Long, Offer>()
  private val nextId = AtomicLong()

  fun offer(from: TradeLinks.Partner, fromAccountId: Long, toMasterId: Long, fromLevel: Int): Long {
    val id = nextId.incrementAndGet()
    offers[id] = Offer(from, fromAccountId, toMasterId, fromLevel, clock.instant().plus(LIFETIME))

    return id
  }

  /** The offer [offerId] made to [masterId], removed; null when there is none or it lapsed. */
  fun take(offerId: Long, masterId: Long): Offer? {
    val offer = offers[offerId]?.takeIf { it.toMasterId == masterId } ?: return null
    offers.remove(offerId)

    return offer.takeIf { it.expiresAt.isAfter(clock.instant()) }
  }

  private companion object {
    val LIFETIME: Duration = Duration.ofMinutes(1)
  }
}
