package net.bestia.zone.cartography.tools

import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.render.optionalAttribute
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.cartography.render.TileInputs
import net.bestia.zone.cartography.tile.MapWorldKey
import net.bestia.zone.cartography.tile.TileId
import net.bestia.zone.cartography.tile.TileRenderer
import net.bestia.zone.cartography.tile.TileStore
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Bakes the tile pyramid to disk, and reports what it cost.
 *
 * ```
 * ./gradlew :zone-server:mapBake -Pgenesis                       # coarse levels whole, fine ones round towns
 * ./gradlew :zone-server:mapBake -Pgenesis -Plevels=9..5         # only these levels, whole world
 * ./gradlew :zone-server:mapBake -Pgenesis -Pforce               # re-render tiles already on disk
 * ```
 *
 * ### Why the default is not "every level, everywhere"
 *
 * The pyramid is not uniform in cost or in value. On the 128 km Genesis world, L9 down to L5 is 341 tiles of
 * raster sampling; L0 over the whole world is 262 144 tiles, most of them empty grassland, each needing a
 * per-pixel evaluation of the detail heightfield. So the default bakes the coarse levels whole - they are what a
 * player opening the world map sees, and they are cheap - and the fine levels only inside settlement bounds,
 * which is the only place the plan style has anything to draw that the atlas did not already show.
 *
 * Everything else is left for the tile service to render on demand into the same cache. This tool exists to
 * make a *first* request fast, not to be the only way a tile is ever produced.
 */
object MapBakeMain {

  private const val LEVELS = "--levels"
  private const val OUT = "--out"
  private const val FORCE = "--force"
  private const val THREADS = "--threads"
  private const val WHOLE = "--whole"

  private val FLAGS = setOf(LEVELS, OUT, FORCE, THREADS, WHOLE)

  @JvmStatic
  fun main(argv: Array<String>) {
    val args = MapToolArgs.parse(argv, FLAGS)
    val config = args.config

    val startedAt = System.nanoTime()
    val generated = StandardWorld.build(config, params = args.params)
    val inputs = TileInputs.of(generated)
    val key = MapWorldKey.of(generated)
    val store = TileStore(args.file(OUT, DEFAULT_ROOT), key)

    val fit = TileId.fitLevel(config.widthMetres)
    val levels = args.string(LEVELS)?.let(::parseLevels) ?: (0..fit).reversed().toList()
    val force = args.has(FORCE)
    val threads = args.int(THREADS, Runtime.getRuntime().availableProcessors().coerceAtMost(MAX_THREADS))

    println(
      "world %.0f x %.0f km, fits one tile at L%d, key %s".format(
        Locale.ROOT, config.widthMetres / 1000.0, config.heightMetres / 1000.0, fit, key
      )
    )
    println("baking into ${store.directory.absolutePath} on $threads threads")
    println()
    println("%-6s %10s %10s %10s %12s %10s".format(Locale.ROOT, "level", "m/px", "tiles", "skipped", "mean bytes", "seconds"))

    val whole = args.has(WHOLE)
    val towns = if (whole) emptyList() else townBounds(inputs)

    for (level in levels) {
      bakeLevel(level, config.widthMetres, config.heightMetres, towns, whole, store, inputs, force, threads)
    }

    println()
    println("cache now holds ${store.measure()}")
    println("total %.1f s".format(Locale.ROOT, (System.nanoTime() - startedAt) / 1e9))
  }

  private fun bakeLevel(
    level: Int,
    worldWidth: Double,
    worldHeight: Double,
    towns: List<Aabb>,
    whole: Boolean,
    store: TileStore,
    inputs: TileInputs,
    force: Boolean,
    threads: Int
  ) {
    val tiles = tilesFor(level, worldWidth, worldHeight, towns, whole)
    val wanted = if (force) tiles else tiles.filterNot(store::has)
    val skipped = tiles.size - wanted.size

    val startedAt = System.nanoTime()
    val bytes = AtomicLong()

    if (wanted.isNotEmpty()) {
      val pool = Executors.newFixedThreadPool(threads) { runnable ->
        Thread(runnable, "map-bake").apply { isDaemon = true }
      }
      try {
        // One renderer per worker. The styles are stateless but the fields they build are not necessarily, and
        // sharing one instance across threads would be relying on that rather than on the documented contract.
        val local = ThreadLocal.withInitial { TileRenderer(inputs) }
        val futures = wanted.map { tile ->
          pool.submit {
            val encoded = local.get().encode(tile)
            store.write(tile, encoded)
            bytes.addAndGet(encoded.size.toLong())
          }
        }
        futures.forEach { it.get() }
      } finally {
        pool.shutdown()
        pool.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)
      }
    }

    val seconds = (System.nanoTime() - startedAt) / 1e9
    val mean = if (wanted.isEmpty()) 0 else bytes.get() / wanted.size

    println(
      "L%-5d %10.1f %10d %10d %12d %10.1f".format(
        Locale.ROOT, level, Math.pow(2.0, level.toDouble()), tiles.size, skipped, mean, seconds
      )
    )
  }

  /**
   * Which tiles a level needs: the whole world where the atlas draws, settlement surroundings where the plan
   * does.
   *
   * The split is at [COARSE_FLOOR] rather than at the style boundary, deliberately. Levels 4 and 3 are still
   * atlas tiles, but there are 1024 and 4096 of them on a 128 km world and almost none of that ground differs
   * from what L5 already showed - so they are treated as fine levels and baked only where somebody is likely to
   * look. A player who zooms in over empty moor still gets a tile; it is rendered on request.
   */
  private fun tilesFor(
    level: Int,
    worldWidth: Double,
    worldHeight: Double,
    towns: List<Aabb>,
    whole: Boolean
  ): List<TileId> {
    if (whole || level >= COARSE_FLOOR) {
      return TileId.covering(level, Aabb(0.0, 0.0, worldWidth, worldHeight))
    }

    return towns.flatMap { TileId.covering(level, it) }.distinct()
  }

  /**
   * A box around each settlement, sized by tier.
   *
   * `SettlementTier.footprintRadius` is what the generator itself uses to keep a spawn point out of a town's
   * built area, so it is the right measure of how far the buildings reach; the margin is for the fields and
   * approach roads outside them, which are the part of a plan tile that tells a player they are nearly there.
   */
  private fun townBounds(inputs: TileInputs): List<Aabb> = inputs
    .featuresIn(Aabb(-WORLD_LIMIT, -WORLD_LIMIT, WORLD_LIMIT, WORLD_LIMIT))
    .filterIsInstance<PointMarker>()
    .filter { it.kind == FeatureKind.SETTLEMENT }
    .mapNotNull { marker ->
      val tier = marker.optionalAttribute(SettlementChannels.TIER)?.toInt()
        ?.let { SettlementTier.entries.getOrNull(it) } ?: return@mapNotNull null

      val reach = tier.footprintRadius + TOWN_MARGIN_METRES
      Aabb(
        marker.position.x - reach, marker.position.y - reach,
        marker.position.x + reach, marker.position.y + reach
      )
    }

  /** `9..5` or `9`. Descending or ascending; the bake does not care which order it works in. */
  private fun parseLevels(spec: String): List<Int> {
    if (!spec.contains("..")) return listOf(spec.trim().toInt())

    val (from, to) = spec.split("..").map { it.trim().toInt() }
    return if (from <= to) (from..to).toList() else (to..from).reversed().toList()
  }

  private const val DEFAULT_ROOT = "build/map-cache"

  /**
   * Coarsest level treated as "fine": at and above this, the whole world is baked.
   *
   * Five, because a 128 km world has 256 tiles at L5 and 1024 at L4 - the first number is a few seconds and the
   * second is minutes for ground that mostly looks the same as its parent.
   */
  private const val COARSE_FLOOR = 5

  private const val TOWN_MARGIN_METRES = 600.0

  /** Far enough outside any world to mean "every feature". The store is an index, so the query is cheap. */
  private const val WORLD_LIMIT = 1e9

  /**
   * Cap on bake workers.
   *
   * Rendering is CPU-bound and embarrassingly parallel, but every worker holds its own sampled raster and its
   * own chunk-height caches, so the ceiling is memory rather than cores.
   */
  private const val MAX_THREADS = 8

  private const val SHUTDOWN_SECONDS = 30L
}
