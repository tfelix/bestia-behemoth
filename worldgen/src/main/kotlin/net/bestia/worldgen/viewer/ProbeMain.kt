package net.bestia.worldgen.viewer

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Timings
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.geo.DropletParams
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pipeline.WorldParams
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.SurfaceColumns
import java.util.Locale

/**
 * Dumps what one small patch of ground actually looks like, voxel by voxel.
 *
 * The gap this fills: every other view in `viewer/` renders the whole world into a few hundred pixels, so a
 * feature narrower than a kilometre is one pixel or is nothing at all - which is exactly the size of thing that
 * looks wrong once the client draws it at one metre per voxel. This prints a window the size a player sees, so
 * "the ground is brown with thin green streaks on it" becomes a thing that can be read off a terminal.
 *
 * ```
 * ./gradlew :worldgen:probe -Pcells=128                      # land at about 400 m
 * ./gradlew :worldgen:probe -Pgenesis                        # ...in the world zone-server boots
 * ./gradlew :worldgen:probe -Px=32000 -Py=32000 -Pspan=64    # a particular place
 * ./gradlew :worldgen:probe -Psurvey=400                     # hunt for the most mixed patch in the world
 * ```
 */
object ProbeMain {

  @JvmStatic
  fun main(args: Array<String>) {
    val cli = WorldArgs(args.toList(), extraFlags = PROBE_FLAGS)

    // A world small enough to build while you wait, unlike the viewer's: the probe prints one 48 m window,
    // so nothing outside it is worth the seconds a large world costs.
    val config = cli.worldConfig(
      StandardWorld.demoConfig().copy(widthCells = 128, heightCells = 128)
    )
    val span = cli.int("--span") ?: 48

    val tuning = cli.tuning()
    println("world ${WorldArgs.summary(config)}")
    println("  ${tuning.summary()}")

    // `--droplets` turns on chunk-scale droplet erosion, which ships off. This is the only way to look at it:
    // the viewer renders whole worlds so a gully is a pixel, and the probe's 48 m window is the scale the
    // feature exists at. A gated feature nobody can see is a gated feature nobody can judge.
    //
    // A switch on the file's droplet tuning rather than a fresh `DropletParams`, so `--droplets --params f`
    // looks at the density the file asks for. Building the default set here instead would silently discard it.
    val droplets = tuning.params.droplets.copy(enabled = tuning.params.droplets.enabled || cli.has(DROPLETS))
    if (droplets.enabled) println("droplet erosion ON - ${droplets.dropletsPerSquareKilometre.toInt()}/km2")

    val generated = StandardWorld.build(config, params = tuning.params.copy(droplets = droplets))
    // Before the window rather than after it, so a sub-stage table is never mistaken for part of the
    // ground being printed. A no-op unless -Ptimings, so the call site stays.
    Timings.printAndReset()
    val probe = Probe(config, generated)

    val survey = cli.int("--survey")
    if (survey != null) {
      probe.survey(survey, span)
      return
    }

    if (cli.has("--channels")) {
      probe.channels()
      return
    }

    val elevation = generated.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val x = cli.double("--x")
    val y = cli.double("--y")
    val onFeature = cli.value("--on")

    val centre = if (onFeature != null) {
      probe.midpointOf(onFeature, cli.int("--nth") ?: 0)
    } else if (x != null && y != null) {
      x to y
    } else {
      // A coordinate nobody chose is nearly always the wrong one: on a world that is mostly sea, the centre is
      // the seabed four kilometres down, which says nothing at all about ground a player stands on.
      val want = cli.double("--at") ?: 400.0
      probe.nearestLand(elevation, want).also {
        println("  no coordinate given, so: nearest land to ${want.toInt()} m is (${it.first.toInt()}, ${it.second.toInt()})")
      }
    }

    probe.describe(centre.first, centre.second)
    probe.dump(centre.first, centre.second, span)
  }

  /** One material tally over a square window of voxel columns. */
  private class Window(val counts: Map<BlockType, Int>, val minElevation: Double, val maxElevation: Double) {

    val total get() = counts.values.sum()

    /** Share of the window taken by everything except its commonest material. */
    val minorityShare: Double
      get() {
        val dominant = counts.values.maxOrNull() ?: return 0.0
        return (total - dominant) / total.toDouble()
      }

    val relief get() = maxElevation - minElevation

    /** False when every column was submerged, so the min and max are still their sentinels. */
    val hasElevation get() = minElevation <= maxElevation
  }

  private class Probe(val config: WorldConfig, val generated: GeneratedWorld) {

    /** Materialising a chunk per voxel would make a 48 m window 2 300 chunk builds. */
    private val chunks = HashMap<Long, SurfaceColumns>()

    private fun columnsOf(chunkX: Int, chunkY: Int): SurfaceColumns =
      chunks.getOrPut(chunkX.toLong() shl 32 or (chunkY.toLong() and 0xFFFFFFFFL)) {
        generated.materializer.surfaceColumns(chunkX, chunkY)
      }

    private fun blockAt(worldX: Double, worldY: Double): Pair<BlockType, Double> {
      val voxelX = Math.floor(worldX / config.voxelSize).toInt()
      val voxelY = Math.floor(worldY / config.voxelSize).toInt()
      val columns = columnsOf(
        Math.floorDiv(voxelX, config.chunkSize),
        Math.floorDiv(voxelY, config.chunkSize)
      )
      val localX = Math.floorMod(voxelX, config.chunkSize)
      val localY = Math.floorMod(voxelY, config.chunkSize)

      return BlockType.of(columns.blockAt(localX, localY)) to columns.elevationAt(localX, localY)
    }

    private fun window(centreX: Double, centreY: Double, span: Int): Window {
      val half = span / 2
      val counts = HashMap<BlockType, Int>()
      var lowest = Double.MAX_VALUE
      var highest = -Double.MAX_VALUE

      for (dy in -half..half) {
        for (dx in -half..half) {
          val (block, elevation) = blockAt(centreX + dx * config.voxelSize, centreY + dy * config.voxelSize)
          counts[block] = (counts[block] ?: 0) + 1
          if (!elevation.isNaN()) {
            lowest = minOf(lowest, elevation)
            highest = maxOf(highest, elevation)
          }
        }
      }

      return Window(counts, lowest, highest)
    }

    fun describe(centreX: Double, centreY: Double) {
      val layers = generated.world.layers
      val biome = layers.require<IntLayer>(LayerId.BIOME)
      val temperature = layers.require<FloatLayer>(LayerId.TEMPERATURE)
      val precipitation = layers.require<FloatLayer>(LayerId.PRECIPITATION)
      val discharge = layers.require<FloatLayer>(LayerId.DISCHARGE)
      val waterLevel = layers.require<FloatLayer>(LayerId.WATER_LEVEL)
      val lakeId = layers.require<IntLayer>(LayerId.LAKE_ID)

      println()
      println("at (${centreX.toInt()}, ${centreY.toInt()}):")
      println("  coarse biome    ${Biome.of(biome.sampleNearest(centreX, centreY))}")
      println("  temperature     ${"%.1f".format(Locale.ROOT, temperature.sampleBilinear(centreX, centreY))} C")
      println("  precipitation   ${"%.0f".format(Locale.ROOT, precipitation.sampleBilinear(centreX, centreY))} mm")
      println("  discharge       ${"%.4f".format(Locale.ROOT, discharge.sampleBilinear(centreX, centreY))}")
      println("  lake id         ${lakeId.sampleNearest(centreX, centreY)}")
      println("  water level     ${waterLevel.sampleBilinear(centreX, centreY)}")
      println("  base height     ${"%.3f".format(Locale.ROOT, generated.base.heightAt(centreX, centreY))} m")
    }

    fun dump(centreX: Double, centreY: Double, span: Int) {
      val half = span / 2
      val glyphs = LinkedHashMap<BlockType, Char>()
      val rows = ArrayList<String>()

      // North up, so the rows read the way the exported PNGs do.
      for (dy in half downTo -half) {
        val row = StringBuilder()
        for (dx in -half..half) {
          val (block, _) = blockAt(centreX + dx * config.voxelSize, centreY + dy * config.voxelSize)
          row.append(glyphs.getOrPut(block) { GLYPHS[glyphs.size % GLYPHS.length] })
        }
        rows.add(row.toString())
      }

      val measured = window(centreX, centreY, span)

      println()
      println("surface block over a ${span}x${span} m window (north up):")
      rows.forEach { println("  $it") }

      println()
      println("legend, commonest first:")
      measured.counts.entries.sortedByDescending { it.value }.forEach { (block, count) ->
        val share = 100.0 * count / measured.total
        println("  ${glyphs[block]}  ${block.name.padEnd(12)} ${count.toString().padStart(5)}  ${"%.1f".format(Locale.ROOT, share)}%")
      }

      println()
      if (measured.hasElevation) {
        println("relief ${"%.2f".format(Locale.ROOT, measured.relief)} m across $span m " +
            "(${"%.3f".format(Locale.ROOT, measured.minElevation)} .. ${"%.3f".format(Locale.ROOT, measured.maxElevation)})")
      } else {
        // Every column submerged: `SurfaceColumns` reports NO_FILL for a surface it cannot see under water, so
        // there is no relief to report. Saying so beats printing the min and max sentinels, which come out as
        // plus and minus 1.8e308 and read as a numerical catastrophe rather than as "this is the sea".
        println("no surface elevation anywhere in the window - every column is under water")
      }
    }

    /**
     * How wide and how deep every river channel in the world actually is, measured against the voxel grid.
     *
     * The number that matters is not the channel count. A channel narrower than a voxel or shallower than one
     * cannot be drawn: its water rounds away to no water voxel at all in some columns and to one in others, as
     * the bed drifts across a voxel boundary along its length, so it renders as a dashed line rather than as a
     * river. Counting how many are in that state is the difference between "the world has 139 rivers" and "the
     * world has 139 things the client cannot draw".
     */
    fun channels() {
      val voxel = config.voxelSize
      val widths = ArrayList<Double>()
      val depths = ArrayList<Double>()

      generated.world.features.all().filterIsInstance<PolylineFeature>()
        .filter { it.kind.name == "RIVER_CHANNEL" }
        .forEach { river ->
          val widthChannel = river.stations.channel(Profiles.CHANNEL_WIDTH)
          val depthChannel = river.stations.channel(Profiles.CHANNEL_DEPTH)
          for (station in 0 until river.stations.stationCount) {
            widths.add(river.stations.valueAt(widthChannel, station))
            depths.add(river.stations.valueAt(depthChannel, station))
          }
        }

      if (widths.isEmpty()) {
        println("no river channels in this world")
        return
      }

      widths.sort()
      depths.sort()

      println()
      println("${widths.size} channel stations across every river, voxel size $voxel m")
      println("                   min      p25      p50      p75      max")
      println("  width m     ${quantiles(widths)}")
      println("  depth m     ${quantiles(depths)}")

      val thin = widths.count { it < voxel }
      val shallow = depths.count { it < voxel }
      println()
      println("  narrower than one voxel  ${percent(thin, widths.size)}")
      println("  shallower than one voxel ${percent(shallow, depths.size)}")
      println("  deep enough to hold a water voxel everywhere along it: " +
          percent(depths.size - shallow, depths.size))
    }

    private fun quantiles(sorted: List<Double>): String {
      fun at(q: Double) = sorted[((sorted.size - 1) * q).toInt()]
      return listOf(0.0, 0.25, 0.5, 0.75, 1.0)
        .joinToString("") { "%9.3f".format(Locale.ROOT, at(it)) }
    }

    private fun percent(n: Int, total: Int) = "$n of $total (${"%.1f".format(Locale.ROOT, 100.0 * n / total)}%)"

    /**
     * A point halfway along the [nth] feature of a kind, so a thin feature can be looked at from on top of it.
     *
     * A survey of cell centres never lands on a river: a channel a metre wide covers a millionth of a kilometre
     * cell, so the only way to see one is to ask the feature where it is.
     */
    fun midpointOf(kind: String, nth: Int): Pair<Double, Double> {
      val matching = generated.world.features.all()
        .filter { it.kind.name.equals(kind, ignoreCase = true) }

      require(matching.isNotEmpty()) {
        "no $kind features; this world has " +
            generated.world.features.all().groupingBy { it.kind.name }.eachCount()
      }

      val feature = matching[nth.coerceIn(0, matching.size - 1)]

      // The midpoint of the centreline, not the centre of the bounding box: a meander's bbox centre can sit on
      // the far bank, which is precisely the wrong place to stand when the question is how wide the channel is.
      val at = if (feature is PolylineFeature) {
        val mid = feature.centerline.pointAt(feature.centerline.length / 2.0)
        println("  on ${feature.kind} ${nth + 1} of ${matching.size}, " +
            "midpoint of a ${feature.centerline.length.toInt()} m centreline")
        mid.x to mid.y
      } else {
        val box = feature.bbox
        println("  on ${feature.kind} ${nth + 1} of ${matching.size}, centre of its bounds")
        ((box.minX + box.maxX) / 2.0) to ((box.minY + box.maxY) / 2.0)
      }

      return at
    }

    /** The dry cell whose elevation is closest to [want], as a world position at its centre. */
    fun nearestLand(elevation: FloatLayer, want: Double): Pair<Double, Double> {
      val metres = config.baseResolution.metresPerCell
      var best = config.widthMetres / 2.0 to config.heightMetres / 2.0
      var bestError = Double.MAX_VALUE

      forEachLandCell(elevation) { x, y, height ->
        val error = Math.abs(height - want)
        if (error < bestError) {
          bestError = error
          best = x to y
        }
      }

      return best
    }

    /**
     * Reports the patches with the most mixed surface, which is where a material bug shows.
     *
     * A uniform window tells you nothing - the interesting places are the ones where two materials meet, because
     * that is where a boundary rule can be wrong in a way a colour map at world scale cannot show.
     */
    fun survey(count: Int, span: Int) {
      val elevation = generated.world.layers.require<FloatLayer>(LayerId.ELEVATION)
      val biome = generated.world.layers.require<IntLayer>(LayerId.BIOME)

      val sampled = ArrayList<Triple<Pair<Double, Double>, Window, Biome>>()
      var visited = 0

      forEachLandCell(elevation) { x, y, _ ->
        // Every nth land cell, so a survey of a few hundred spreads over the whole world rather than over its
        // first few rows.
        if (visited++ % STRIDE == 0) {
          chunks.clear()
          sampled.add(Triple(x to y, window(x, y, span), Biome.of(biome.sampleNearest(x, y))))
        }
      }

      println()
      println("surveyed ${sampled.size} land patches of ${span}x${span} m")

      val mixed = sampled.sortedByDescending { it.second.minorityShare }.take(count.coerceAtMost(sampled.size))
      println()
      println("most mixed patches:")
      mixed.take(12).forEach { (at, window, biome) ->
        val breakdown = window.counts.entries.sortedByDescending { it.value }
          .joinToString(" ") { "${it.key.name}=${"%.1f".format(Locale.ROOT, 100.0 * it.value / window.total)}%" }
        println("  (${at.first.toInt()}, ${at.second.toInt()}) $biome  relief ${"%.1f".format(Locale.ROOT, window.relief)} m  $breakdown")
      }

      // Which materials the world's surface is actually made of, over everything sampled.
      val overall = HashMap<BlockType, Int>()
      sampled.forEach { (_, window, _) -> window.counts.forEach { (block, n) -> overall[block] = (overall[block] ?: 0) + n } }
      val total = overall.values.sum()

      println()
      println("surface material over every sampled patch:")
      overall.entries.sortedByDescending { it.value }.forEach { (block, n) ->
        println("  ${block.name.padEnd(12)} ${"%.2f".format(Locale.ROOT, 100.0 * n / total)}%")
      }

      val biomes = HashMap<Biome, Int>()
      sampled.forEach { (_, _, b) -> biomes[b] = (biomes[b] ?: 0) + 1 }
      println()
      println("coarse biome of every sampled patch:")
      biomes.entries.sortedByDescending { it.value }.forEach { (b, n) ->
        println("  ${b.name.padEnd(18)} $n")
      }
    }

    private inline fun forEachLandCell(elevation: FloatLayer, body: (Double, Double, Double) -> Unit) {
      val metres = config.baseResolution.metresPerCell
      for (cellY in 0 until config.heightCells) {
        for (cellX in 0 until config.widthCells) {
          val x = (cellX + 0.5) * metres
          val y = (cellY + 0.5) * metres
          val height = elevation.sampleBilinear(x, y)
          if (height > config.seaLevel) {
            body(x, y, height)
          }
        }
      }
    }
  }

  /** Every 37th land cell: coprime with any world width, so the walk does not fall into step with the rows. */
  private const val STRIDE = 37

  /** Distinct at a glance in a terminal, quietest glyph first so the commonest material recedes. */
  private const val GLYPHS = ".:oO#*+=%@$&~"

  /** What to look at, as opposed to which world to look at it in - see [WorldArgs]. */
  private const val DROPLETS = "--droplets"

  private val PROBE_FLAGS =
    setOf("--x", "--y", "--span", "--at", "--survey", "--on", "--nth", "--channels", DROPLETS)
}
