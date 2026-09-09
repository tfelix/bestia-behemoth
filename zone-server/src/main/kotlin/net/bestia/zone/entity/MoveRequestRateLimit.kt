package net.bestia.zone.entity

import net.bestia.zone.account.AccountDisconnectedEvent
import net.bestia.zone.ecs.ZoneConfig
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * How often one account may ask to move.
 *
 * A move request costs the client one packet and the server a good deal more: the path is validated on the
 * connection's I/O thread while it holds the world lock, and every accepted one broadcasts a path to
 * everybody in range. Click-to-move produces a few a second at most, so the ceiling is far above what a
 * person can do and far below what spamming it would cost everyone nearby.
 *
 * Refilled from the clock rather than once per tick, unlike `ChunkStreamSystem`'s bucket: this is asked on an
 * I/O thread, where there is no tick to hang a refill on.
 *
 * One limiter for one message is the narrow version of this. The general one belongs in the socket layer,
 * where it would cover every unbounded CMSG rather than only the one that happens to broadcast.
 */
@Component
class MoveRequestRateLimit(private val config: ZoneConfig) {

  private class Bucket(var tokens: Float, var lastRefillMs: Long)

  private val buckets = ConcurrentHashMap<Long, Bucket>()

  /**
   * Takes a token for [accountId] and reports whether there was one to take.
   *
   * @return false when the account is over its rate, in which case the request belongs in the bin
   */
  fun spend(accountId: Long): Boolean {
    val bucket = buckets.computeIfAbsent(accountId) {
      Bucket(config.moveRequestBurst, System.currentTimeMillis())
    }

    // Per bucket rather than over the map: two connections are never the same account, so they never
    // contend, and one account's own requests arrive on a single Netty channel anyway.
    synchronized(bucket) {
      val now = System.currentTimeMillis()
      val refilled = bucket.tokens + (now - bucket.lastRefillMs) / 1000f * config.moveRequestsPerSecond

      bucket.tokens = minOf(config.moveRequestBurst, refilled)
      bucket.lastRefillMs = now

      if (bucket.tokens < 1f) {
        return false
      }

      bucket.tokens -= 1f

      return true
    }
  }

  @EventListener
  fun handleAccountDisconnected(event: AccountDisconnectedEvent) {
    buckets.remove(event.accountId)
  }
}
