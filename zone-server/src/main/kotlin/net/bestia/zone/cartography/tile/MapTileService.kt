package net.bestia.zone.cartography.tile

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import net.bestia.zone.cartography.CartographyConfig
import net.bestia.zone.cartography.chart.ChartService
import net.bestia.zone.cartography.coverage.AreaCoverage
import net.bestia.zone.cartography.coverage.Coverage
import net.bestia.zone.cartography.render.TileInputs
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Answers "what may this master see of this tile", and produces the bytes.
 *
 * ### The three answers are three different amounts of work
 *
 * A tile whose surroundings are completely charted is the **base** tile: the same bytes for everyone, straight
 * off disk, and cacheable forever because nothing about it depends on who asked. A tile with no charted ground
 * in it is **absent** - never rendered, so not even its size leaks. Everything between is **masked**, keyed by a
 * digest of only the coverage bits inside that tile, so two players on the same frontier share one render.
 *
 * The completeness test asks about [FogMask.clearingArea] rather than the tile's own bounds, which is not a
 * detail: coverage stopping exactly on a tile edge still fringes *inside* that tile, so the narrower test would
 * serve it unmasked against a neighbour showing a fade.
 *
 * ### Rendering is bounded and never on the tick thread
 *
 * Tiles are rendered on a small pool of its own rather than on whichever servlet thread asked, so a burst of
 * requests cannot turn into a burst of concurrent renders - each of which holds a sampled raster and its own
 * chunk-height caches, making the ceiling memory rather than cores. Nothing here touches the ECS: the layers are
 * plain arrays, the feature store is frozen after generation, and `ChunkHeightSampler` documents itself as
 * stateless. `world/stream/ChunkService` is the thing it must never reach, and does not.
 */
@Service
class MapTileService(
  private val worldService: WorldService,
  private val chartService: ChartService,
  private val config: CartographyConfig,
) {

  /** A tile ready to send, and how the client is allowed to cache it. */
  data class Tile(val bytes: ByteArray, val etag: String, val shared: Boolean) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Tile

      if (shared != other.shared) return false
      if (!bytes.contentEquals(other.bytes)) return false
      if (etag != other.etag) return false

      return true
    }

    override fun hashCode(): Int {
      var result = shared.hashCode()
      result = 31 * result + bytes.contentHashCode()
      result = 31 * result + etag.hashCode()
      return result
    }
  }

  private val workers = Executors.newFixedThreadPool(RENDER_THREADS) { runnable ->
    Thread(runnable, "map-render").apply { isDaemon = true }
  }

  private val renderers = ThreadLocal.withInitial { TileRenderer(resources.inputs) }

  /** Masked tiles, keyed by tile and coverage digest. Bounded by count; a tile is tens of kilobytes. */
  private val masked = ConcurrentHashMap<String, ByteArray>()

  /**
   * The coverage a master can see, remembered briefly.
   *
   * Not invalidated on change, expired on a timer, and that is the honest shape for it. `ChartService` sees
   * minting, merging and copying - but not a chart dropped, looted or eventually traded, so a cache with
   * explicit invalidation would have some of its invalidation points and look like it had all of them. A short
   * expiry has *bounded* staleness instead, which is a thing that can be reasoned about: a survey shows up on
   * the map within [COVERAGE_TTL_MILLIS], and opening the map does not run twenty identical queries.
   */
  private val coverageCache = ConcurrentHashMap<Long, CachedCoverage>()

  private class CachedCoverage(val coverage: Coverage, val expiresAtNanos: Long)

  /** Built on first use rather than at construction: the world is not generated until `WorldService.load`. */
  private val resources: Resources by lazy {
    val generated = worldService.generated
    val key = MapWorldKey.of(generated)

    LOG.info { "Map tiles for world key $key under ${File(config.cacheDir, key.value).absolutePath}" }
    Resources(
      inputs = TileInputs.of(generated),
      key = key,
      store = TileStore(File(config.cacheDir), key),
      fitLevel = TileId.fitLevel(generated.config.widthMetres)
    )
  }

  private class Resources(
    val inputs: TileInputs,
    val key: MapWorldKey,
    val store: TileStore,
    val fitLevel: Int
  )

  /** World geometry and the cache key a client needs before it can ask for anything. */
  fun meta(): Map<String, Any> {
    val config = worldService.config

    return mapOf(
      "worldMapVersion" to resources.key.value,
      "tileSize" to TileId.TILE_PIXELS,
      "minLevel" to 0,
      "maxLevel" to resources.fitLevel,
      "worldWidthMetres" to config.widthMetres,
      "worldHeightMetres" to config.heightMetres,
      "metresPerVoxel" to config.voxelSize
    )
  }

  /**
   * The tile, or null when the master has charted nothing in it.
   *
   * @throws IllegalArgumentException for a level outside the pyramid, which is a malformed request rather than
   *   an empty answer
   */
  fun tile(masterId: Long, id: TileId): Tile? {
    require(id.level in 0..resources.fitLevel) {
      "Level ${id.level} is outside 0..${resources.fitLevel}"
    }

    val coverage = coverageFor(masterId)

    if (coverage.coverageOf(FogMask.clearingArea(id)) == AreaCoverage.Full) {
      return Tile(base(id), etag = "\"${resources.key}-${id.path()}\"", shared = true)
    }

    val inside = coverage.coverageOf(id.bounds)
    if (inside == AreaCoverage.None) return null

    val digest = (inside as AreaCoverage.Partial).digest
    val cacheKey = "${id.path()}#$digest"
    masked[cacheKey]?.let { return Tile(it, etag = "\"${resources.key}-$cacheKey\"", shared = false) }

    val mask = FogMask.forTile(id, coverage)
    // Reachable despite the None check above: that test is exact on cells, and this one accounts for the
    // fringe swallowing a lone charted cell after quantisation.
    if (mask.isFullyHidden) return null

    val bytes = render { renderers.get().encode(id, mask) }
    if (masked.size < MAX_MASKED_TILES) masked[cacheKey] = bytes

    return Tile(bytes, etag = "\"${resources.key}-$cacheKey\"", shared = false)
  }

  /** Base bytes from the on-disk cache, rendering into it on a miss. */
  private fun base(id: TileId): ByteArray {
    resources.store.read(id)?.let { return it }

    val bytes = render { renderers.get().encode(id) }
    resources.store.write(id, bytes)
    return bytes
  }

  private fun coverageFor(masterId: Long): Coverage {
    val now = System.nanoTime()
    coverageCache[masterId]?.let { if (it.expiresAtNanos > now) return it.coverage }

    val coverage = chartService.inventoryCoverage(masterId)
    coverageCache[masterId] = CachedCoverage(
      coverage,
      now + TimeUnit.MILLISECONDS.toNanos(COVERAGE_TTL_MILLIS)
    )

    // Cheap sweep rather than a scheduled one: the map is the only thing that puts entries here, and a player
    // who stops looking at it stops being swept - which is fine, the entries are small and idempotent.
    if (coverageCache.size > MAX_CACHED_COVERAGES) {
      coverageCache.entries.removeIf { it.value.expiresAtNanos <= now }
    }

    return coverage
  }

  private fun render(work: () -> ByteArray): ByteArray {
    val future = workers.submit(work)
    return try {
      future.get(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    } catch (e: TimeoutException) {
      future.cancel(true)
      throw IllegalStateException("Rendering a tile took longer than $RENDER_TIMEOUT_SECONDS s", e)
    }
  }

  @PreDestroy
  fun shutdown() {
    workers.shutdownNow()
  }

  private companion object {

    private val LOG = KotlinLogging.logger { }

    /** Memory rather than cores is the ceiling: every worker holds its own raster and chunk-height caches. */
    val RENDER_THREADS = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)

    /**
     * How long a master's charted area is reused for.
     *
     * Two seconds: long enough that opening the map is one query rather than twenty, short enough that a player
     * who surveys and then looks at their map sees the new ground on their second glance at worst.
     */
    const val COVERAGE_TTL_MILLIS = 2_000L

    /** Well past a full screen of tiles for every frontier being explored on a small server. */
    const val MAX_MASKED_TILES = 2_000

    const val MAX_CACHED_COVERAGES = 500

    /** A cold plan tile samples the detail heightfield per pixel, which is seconds rather than milliseconds. */
    const val RENDER_TIMEOUT_SECONDS = 30L
  }
}
