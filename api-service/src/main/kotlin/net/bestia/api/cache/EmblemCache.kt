package net.bestia.api.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component

/** Guild emblems by the hash that names them. A hash never names another picture, so nothing here goes stale. */
@Component
class EmblemCache {

  private val pngByHash: Cache<String, ByteArray> = Caffeine.newBuilder()
    .maximumWeight(MAX_BYTES)
    .weigher<String, ByteArray> { _, png -> png.size }
    .build()

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
