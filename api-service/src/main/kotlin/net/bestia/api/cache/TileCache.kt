package net.bestia.api.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component

/**
 * Tile bytes by ETag, and the last ETag seen per master and tile. A repeat request is then sent to the zone as a
 * conditional one, and a 304 from the zone is enough to answer from here: the PNG crosses the bus once.
 */
@Component
class TileCache {

  private val bytesByEtag: Cache<String, ByteArray> = Caffeine.newBuilder()
    .maximumWeight(MAX_BYTES)
    .weigher<String, ByteArray> { _, bytes -> bytes.size }
    .build()

  private val etagByTile: Cache<String, String> = Caffeine.newBuilder()
    .maximumSize(MAX_TILES)
    .build()

  fun knownEtag(masterId: Long, path: String): String? {
    return etagByTile.getIfPresent("$masterId:$path")
  }

  fun bytes(etag: String): ByteArray? {
    return bytesByEtag.getIfPresent(etag)
  }

  fun remember(masterId: Long, path: String, etag: String, bytes: ByteArray) {
    bytesByEtag.put(etag, bytes)
    etagByTile.put("$masterId:$path", etag)
  }

  fun forgetMaster(masterId: Long) {
    etagByTile.asMap().keys.removeIf { it.startsWith("$masterId:") }
  }

  private companion object {
    const val MAX_BYTES = 64L * 1024 * 1024
    const val MAX_TILES = 100_000L
  }
}
