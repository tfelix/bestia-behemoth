package net.bestia.api.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Recent answers per account, so an app that redraws a screen does not ask the zone again. Short-lived, and
 * dropped for an account as soon as an event says something of its changed.
 */
@Component
class AnswerCache(registry: MeterRegistry) {

  private data class Key(val accountId: Long, val query: ZoneQuery)

  private val answers: Cache<Key, ZoneAnswer> = Caffeine.newBuilder()
    .expireAfterWrite(TIME_TO_LIVE)
    .maximumSize(MAX_ANSWERS)
    .recordStats()
    .build<Key, ZoneAnswer>()
    .also { CaffeineCacheMetrics.monitor(registry, it, "answers") }

  fun <T : ZoneAnswer> get(accountId: Long, query: ZoneQuery, ask: () -> T): T {
    @Suppress("UNCHECKED_CAST")
    return answers.get(Key(accountId, query)) { ask() } as T
  }

  fun forget(accountId: Long) {
    answers.asMap().keys.removeIf { it.accountId == accountId }
  }

  private companion object {
    val TIME_TO_LIVE: Duration = Duration.ofSeconds(5)
    const val MAX_ANSWERS = 10_000L
  }
}
