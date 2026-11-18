package net.bestia.api.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import org.springframework.stereotype.Component

/** Written texts by id and language. A written body never changes, so nothing here goes stale. */
@Component
class TextCache(registry: MeterRegistry) {

  private val bodyByIdAndLocale: Cache<String, String> = Caffeine.newBuilder()
    .maximumWeight(MAX_CHARS)
    .weigher<String, String> { _, body -> body.length }
    .recordStats()
    .build<String, String>()
    .also { CaffeineCacheMetrics.monitor(registry, it, "texts") }

  fun body(textId: String, locale: String): String? {
    return bodyByIdAndLocale.getIfPresent(keyOf(textId, locale))
  }

  fun remember(textId: String, locale: String, body: String) {
    bodyByIdAndLocale.put(keyOf(textId, locale), body)
  }

  private fun keyOf(textId: String, locale: String): String {
    return "$textId/$locale"
  }

  private companion object {
    const val MAX_CHARS = 8L * 1024 * 1024
  }
}
