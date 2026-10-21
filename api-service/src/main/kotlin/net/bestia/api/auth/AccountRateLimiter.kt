package net.bestia.api.auth

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** A fixed one-minute window of calls per account, so one app cannot flood the zone with questions. */
@Component
class AccountRateLimiter(private val properties: CompanionProperties) {

  private val counts = ConcurrentHashMap<Long, AtomicInteger>()

  fun tryAcquire(accountId: Long): Boolean {
    return counts.computeIfAbsent(accountId) { AtomicInteger() }.incrementAndGet() <= properties.requestsPerMinute
  }

  @Scheduled(fixedRate = 60_000)
  fun newWindow() {
    counts.clear()
  }
}
