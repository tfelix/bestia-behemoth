package net.bestia.zone.cartography.tile

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import net.bestia.zone.cartography.CartographyConfig
import net.bestia.zone.cartography.chart.ChartService
import net.bestia.zone.cartography.chart.ChartsChangedEvent
import net.bestia.zone.cartography.coverage.AreaCoverage
import net.bestia.zone.cartography.coverage.Coverage
import net.bestia.zone.cartography.render.TileInputs
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO

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

  /**
   * The render pool did not answer in time.
   *
   * Its own type rather than an `IllegalStateException`, so the controller can tell a saturated pool from a
   * genuine bug. The difference reaches the player: a tile refused as 500 is one the client has no reason to
   * ask for again, and a cold zoom level that fails once then stays fog for the whole session.
   */
  class RenderTimedOut(message: String, cause: Throwable) : RuntimeException(message, cause)

  private val workers = Executors.newFixedThreadPool(RENDER_THREADS) { runnable ->
    Thread(runnable, "map-render").apply { isDaemon = true }
  }

  private val renderers = ThreadLocal.withInitial { TileRenderer(resources.inputs) }

  /** Masked tiles, keyed by tile and coverage digest. Bounded by count; a tile is tens of kilobytes. */
  private val masked = ConcurrentHashMap<String, ByteArray>()

  /**
   * The coverage a master can see, remembered briefly.
   *
   * Expired on a timer *and* dropped on [ChartsChangedEvent], which is the pairing rather than a belt and
   * braces. `ChartService` sees minting, merging and copying but not a chart dropped, looted or eventually
   * traded - so invalidation alone would have some of its invalidation points while looking like it had all of
   * them, and the timer is what keeps the staleness bounded for the ones it cannot see.
   *
   * The event is what surveying needs, because a stale answer there is not merely late. An uncharted tile is a
   * 404 and the client remembers a 404 for as long as it holds the same charts - and it drops that memory the
   * instant its inventory shows the new chart, which is *before* [COVERAGE_TTL_MILLIS] is up. So every tile it
   * asked for in that window was told, permanently, that its owner had charted none of that ground: survey,
   * open the map, and the zoom level you were looking at is fog for the rest of the session.
   */
  private val coverageCache = ConcurrentHashMap<Long, CachedCoverage>()

  private class CachedCoverage(val coverage: Coverage, val expiresAtNanos: Long)

  /** Built on first use rather than at construction: the world is not generated until `WorldService.load`. */
  private val resources: Resources by lazy {
    val generated = worldService.generated
    val key = MapWorldKey.of(generated)

    val width = generated.config.widthMetres
    val height = generated.config.heightMetres

    LOG.info { "Map tiles for world key $key under ${File(config.cacheDir, key.value).absolutePath}" }
    Resources(
      inputs = TileInputs.of(generated),
      key = key,
      store = TileStore(File(config.cacheDir), key),
      // The longer edge: a world taller than it is wide still needs a level its whole map fits inside.
      fitLevel = TileId.fitLevel(maxOf(width, height)),
      widthMetres = width,
      heightMetres = height
    )
  }

  private class Resources(
    val inputs: TileInputs,
    val key: MapWorldKey,
    val store: TileStore,
    val fitLevel: Int,
    val widthMetres: Double,
    val heightMetres: Double
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

    if (!holdsTile(id)) return null

    val coverage = coverageFor(masterId)

    if (coverage.coverageOf(FogMask.clearingArea(id)) == AreaCoverage.Full) {
      return Tile(base(id), etag = "\"${resources.key}-${id.path()}\"", shared = true)
    }

    if (coverage.coverageOf(id.bounds) == AreaCoverage.None) return null

    // Digested over the ground the mask *reads*, never over the tile alone, and the two are genuinely
    // different questions. The tile's own cells can be complete while the falloff margin around them is not -
    // which is the whole ring just inside a chart's edge - and asking there for a digest that only a partly
    // charted area has produces no answer at all. It is also the honest cache key: the fringe is built from
    // the neighbouring cells, so two chart sets that agree inside the tile and differ just outside it draw
    // different tiles and must not share one.
    val digest = when (val around = coverage.coverageOf(FogMask.readArea(id))) {
      is AreaCoverage.Partial -> around.digest
      // Unreachable in both directions - `readArea` contains the area found not to be `Full` above, and
      // contains a tile found not to be `None`. Answered rather than thrown, so that if either area is ever
      // reshaped the cost is a cache that collides instead of a request that fails.
      else -> UNKEYED_DIGEST
    }

    val cacheKey = "${id.path()}#$digest"
    masked[cacheKey]?.let { return Tile(it, etag = "\"${resources.key}-$cacheKey\"", shared = false) }

    val mask = FogMask.forTile(id, coverage)
    // Reachable despite the None check above: that test is exact on cells, and this one accounts for the
    // fringe swallowing a lone charted cell after quantisation.
    if (mask.isFullyHidden) return null

    val bytes = render { renderers.get().encode(baseImage(id), mask) }
    if (masked.size < MAX_MASKED_TILES) masked[cacheKey] = bytes

    return Tile(bytes, etag = "\"${resources.key}-$cacheKey\"", shared = false)
  }

  /**
   * Whether the world has this tile at all.
   *
   * A decision rather than a formality, because the world wraps and the two halves of the map disagree about
   * that. [net.bestia.zone.cartography.coverage.SurveyGrid] folds an out-of-world cell back onto a real one,
   * so the coverage of the tile one world-width east *is* the coverage of this one - but `TerrainRaster` does
   * not wrap and answers NaN out there. Serving those tiles therefore hands back blank parchment carrying a
   * true fog mask: the imagery is of nowhere, and the shape cut out of it still says where the player has
   * charted. Answering 404 is also what stops the map drawing the world over and over at the coarse levels,
   * where a panel is several world-widths across.
   */
  private fun holdsTile(id: TileId): Boolean =
    id.tx in 0 until TileId.tilesAcross(resources.widthMetres, id.level) &&
        id.ty in 0 until TileId.tilesAcross(resources.heightMetres, id.level)

  /** Base bytes from the on-disk cache, rendering into it on a miss. */
  private fun base(id: TileId): ByteArray {
    resources.store.read(id)?.let { return it }

    val bytes = render { renderers.get().encode(id) }
    resources.store.write(id, bytes)
    return bytes
  }

  /**
   * The same base tile as an image, for a mask to be laid over.
   *
   * A masked tile is the base tile plus an alpha channel, so rendering one from scratch was paying a full
   * style pass for a picture the cache very often already held - and paying it again on the next request for
   * the same ground under a different chart set. Worse, it meant `mapBake` was dead weight for exactly the
   * players who need it most: a tile is only served unmasked once its *surroundings* are completely charted,
   * so until then every request re-drew ground that was already on disk.
   *
   * Decoding cannot drift from rendering. The stored bytes are PNG, which is lossless, and they were written
   * from [TileRenderer.draw] - already quantised - so laying a mask over them gives the identical result.
   *
   * Rendered tiles are written on the way past for the same reason [base] writes them: the base is what every
   * *other* chart set masks too, so paying for it once here is what stops the next frontier tile paying again.
   */
  private fun baseImage(id: TileId): BufferedImage {
    val renderer = renderers.get()

    resources.store.read(id)?.let { bytes ->
      try {
        ImageIO.read(ByteArrayInputStream(bytes))?.let { return it }
        LOG.warn { "Cached tile $id decoded to nothing, re-rendering it" }
      } catch (e: IOException) {
        // A cache is allowed to fail; re-rendering loses time and nothing else.
        LOG.warn(e) { "Cached tile $id could not be decoded, re-rendering it" }
      }
    }

    val image = renderer.draw(id)
    resources.store.write(id, renderer.encode(image, mask = null))
    return image
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

  /**
   * Forgets what a master had charted, so the next tile re-reads it.
   *
   * After the commit, not on publication: the eviction has to outlive the write it answers to, and a request
   * already in flight for the same master would otherwise re-fill the cache from the uncommitted state and put
   * the staleness straight back. [TransactionalEventListener] drops an event published with no transaction
   * around it, hence the fallback - a caller that wrote a chart outside one still gets the eviction.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  fun onChartsChanged(event: ChartsChangedEvent) {
    coverageCache.remove(event.masterId)
  }

  private fun render(work: () -> ByteArray): ByteArray {
    val future = workers.submit(work)
    return try {
      future.get(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    } catch (e: TimeoutException) {
      future.cancel(true)
      throw RenderTimedOut("Rendering a tile took longer than $RENDER_TIMEOUT_SECONDS s", e)
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

    /**
     * How long one tile may take before the request is answered as retryable rather than waited on any longer.
     *
     * Far above what a render actually costs: `mapBake` measures Genesis at a few milliseconds a tile even at
     * L0, where the detail heightfield is sampled per pixel. Reaching this therefore means the pool is starved
     * or wedged, not that the tile is expensive - so it is deliberately generous, and the price of being wrong
     * about that is one tile the client asks for again a moment later.
     */
    const val RENDER_TIMEOUT_SECONDS = 30L

    /** Stands in for a coverage digest in the case that cannot arise. See its only use. */
    const val UNKEYED_DIGEST = 0L
  }
}
