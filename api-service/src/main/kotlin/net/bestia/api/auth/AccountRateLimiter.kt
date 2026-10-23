package net.bestia.api.auth

import net.bestia.api.metrics.AuthMetrics
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A fixed one-minute window of calls per account, so one app cannot flood the zone with questions. Map calls count
 * apart and get more room: a map opening asks for dozens of tiles at once, and the zone's render pool already
 * bounds what they cost it.
 */
@Component
class AccountRateLimiter(private val properties: CompanionProperties, private val metrics: AuthMetrics) {

  private data class Bucket(val accountId: Long, val map: Boolean)

  private val counts = ConcurrentHashMap<Bucket, AtomicInteger>()

  fun tryAcquire(accountId: Long, map: Boolean): Boolean {
    val limit = if (map) properties.mapRequestsPerMinute else properties.requestsPerMinute
    val allowed = counts.computeIfAbsent(Bucket(accountId, map)) { AtomicInteger() }.incrementAndGet() <= limit
    if (!allowed) {
      metrics.rateLimited(map)
    }
    return allowed
  }

  @Scheduled(fixedRate = 60_000)
  fun newWindow() {
    counts.clear()
  }
}
