package net.bestia.api.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import org.springframework.stereotype.Component

/** Guild emblems by the hash that names them. A hash never names another picture, so nothing here goes stale. */
@Component
class EmblemCache(registry: MeterRegistry) {

  private val pngByHash: Cache<String, ByteArray> = Caffeine.newBuilder()
    .maximumWeight(MAX_BYTES)
    .weigher<String, ByteArray> { _, png -> png.size }
    .recordStats()
    .build<String, ByteArray>()
    .also { CaffeineCacheMetrics.monitor(registry, it, "emblems") }

  fun png(hash: String): ByteArray? {
    return pngByHash.getIfPresent(hash)
  }

  fun remember(hash: String, png: ByteArray) {
    pngByHash.put(hash, png)
  }

  private companion object {
    const val MAX_BYTES = 16L * 1024 * 1024
  }
}
