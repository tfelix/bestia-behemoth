package net.bestia.worldgen.viewer

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.ColumnHeights
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Timings
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.PropKind
import net.bestia.worldgen.voxel.SurfaceColumns
import net.bestia.worldgen.voxel.VoxelChunk
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
 * ./gradlew :worldgen:probe -Pon=mine -Psection              # ...and a vertical slice through it
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

    if (cli.has(ECOTONE)) {
      probe.ecotone()
      return
    }

    if (cli.has(PROPS)) {
      probe.props(cli.int("--nth") ?: 0, cli.value("--on"), cli.double("--x"), cli.double("--y"), span)
      return
    }

    if (cli.has(STEEPNESS)) {
      probe.steepness(generated.world.layers.require<FloatLayer>(LayerId.ELEVATION), span)
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

    // Both views, not one instead of the other. A shaft is a ring of collar in the plan view and a hole in
    // the section, and either alone can be produced by a bug that the pair cannot: an intact plan view over
    // an empty section is a hole with a lid, and a hole in the section under undisturbed grass is a shaft
    // whose collar never materialised.
    if (cli.has(SECTION)) {
      probe.section(centre.first, centre.second, span, cli.int(BELOW) ?: 24)
    }
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

    /** Whole slabs, for [section]. A plan view never needs one; a section is nothing but interior voxels. */
    private val slabs = HashMap<ChunkPos, VoxelChunk>()

    private val heights = HashMap<ChunkPos, ColumnHeights>()

    private fun columnsOf(chunkX: Int, chunkY: Int): SurfaceColumns =
      chunks.getOrPut(chunkX.toLong() shl 32 or (chunkY.toLong() and 0xFFFFFFFFL)) {
        generated.materializer.surfaceColumns(chunkX, chunkY)
      }

    /** The material of one voxel anywhere in the world, by global voxel index. */
    private fun voxelAt(voxelX: Int, voxelY: Int, globalZ: Int): BlockType {
      val chunk = ChunkPos(
        Math.floorDiv(voxelX, config.chunkSize),
        Math.floorDiv(voxelY, config.chunkSize),
        Math.floorDiv(globalZ, config.chunkHeight)
      )
      val slab = slabs.getOrPut(chunk) { generated.materializer.materialize(chunk) }
      return BlockType.of(
        slab.rawAt(
          Math.floorMod(voxelX, config.chunkSize),
          Math.floorMod(voxelY, config.chunkSize),
          Math.floorMod(globalZ, config.chunkHeight)
        )
      )
    }

    /** Terrain height of one voxel column - the heightfield with every vector feature stamped, no blocks. */
    private fun terrainAt(voxelX: Int, voxelY: Int): Double {
      val chunk = ChunkPos(
        Math.floorDiv(voxelX, config.chunkSize),
        Math.floorDiv(voxelY, config.chunkSize)
      )
      val column = heights.getOrPut(chunk) { generated.columns.heights(chunk, 0) }
      return column[Math.floorMod(voxelX, config.chunkSize), Math.floorMod(voxelY, config.chunkSize)]
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
      println("  coarse biome    ${Biome.entries[biome.sampleNearest(centreX, centreY)]}")
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
     * A vertical slice through the ground, one character per voxel, looking north.
     *
     * **The view that had to exist before subtraction could.** Every other view here goes through
     * [SurfaceColumns], which reports the topmost non-air voxel - so a shaft, a passage or a cellar under
     * intact ground is *by construction* invisible to all of them, and a carve that silently did nothing looks
     * exactly like one that worked. `-Pon=mine` showing undisturbed grass is on record as the way that failure
     * presents.
     *
     * Air is a space and nothing else is, which is the whole readability decision: a void reads as a void at a
     * glance, and the eye does not have to consult a legend to find the thing being looked for.
     *
     * The vertical window is taken from the terrain along the strip rather than from the chunk grid, so the
     * band printed is the ground and what has been cut out of it, not whichever 256 m slab the ground happens
     * to fall in.
     *
     * @param depth metres to print below the lowest ground in the strip
     */
    fun section(centreX: Double, centreY: Double, span: Int, depth: Int) {
      val half = span / 2
      val voxelY = Math.floor(centreY / config.voxelSize).toInt()
      val centreVoxelX = Math.floor(centreX / config.voxelSize).toInt()

      var lowest = Double.MAX_VALUE
      var highest = -Double.MAX_VALUE
      for (dx in -half..half) {
        val ground = terrainAt(centreVoxelX + dx, voxelY)
        lowest = minOf(lowest, ground)
        highest = maxOf(highest, ground)
      }

      // What stands *over* the ground decides the top of the window, not the ground itself. A fixed few voxels
      // of sky was right while everything in the world was terrain or built into it; a fifteen-metre tree is
      // simply cropped out of the picture by it, and a view that cannot show the thing being added is the
      // reason this method exists in the first place - see the section on subtraction above.
      val groundZ = config.voxelZOf(highest)
      var contentZ = groundZ
      for (dx in -half..half) {
        for (globalZ in groundZ + SECTION_SCAN downTo contentZ + 1) {
          if (voxelAt(centreVoxelX + dx, voxelY, globalZ) != BlockType.AIR) {
            contentZ = globalZ
            break
          }
        }
      }

      val topZ = contentZ + SECTION_SKY
      val bottomZ = config.voxelZOf(lowest) - Math.max(1, depth)

      val glyphs = LinkedHashMap<BlockType, Char>()
      val counts = HashMap<BlockType, Int>()
      val rows = ArrayList<String>()

      // Air first, so it holds the space rather than whichever material the top-left voxel happened to be.
      glyphs[BlockType.AIR] = ' '

      for (globalZ in topZ downTo bottomZ) {
        val row = StringBuilder()
        for (dx in -half..half) {
          val block = voxelAt(centreVoxelX + dx, voxelY, globalZ)
          counts[block] = (counts[block] ?: 0) + 1
          row.append(glyphs.getOrPut(block) { GLYPHS[(glyphs.size - 1) % GLYPHS.length] })
        }
        rows.add("${"%8.1f".format(Locale.ROOT, config.elevationOfVoxel(globalZ))}  $row")
      }

      println()
      println("west-east section ${span} m wide, ${topZ - bottomZ + 1} voxels tall, at y=${centreY.toInt()}:")
      // The terrain the window was sized from, printed because it is the one number a reader cannot recover
      // from the picture: everything visible is what the *materialiser* did, and this is what it was given.
      println("  ground ${"%.1f".format(Locale.ROOT, lowest)} .. ${"%.1f".format(Locale.ROOT, highest)} m " +
          "along the strip, ${depth} m of it printed below the lowest")
      rows.forEach { println("  $it") }

      println()
      println("legend, commonest first (air is blank):")
      counts.entries.sortedByDescending { it.value }.forEach { (block, count) ->
        println("  '${glyphs[block]}' ${block.name.padEnd(12)} ${count.toString().padStart(6)}")
      }

      // The one number that separates a working carve from a no-op, and the reason this method prints anything
      // beyond the picture: air below the ground surface is what subtraction produces and nothing else does.
      var voidVoxels = 0
      for (dx in -half..half) {
        val ground = terrainAt(centreVoxelX + dx, voxelY)
        val surfaceZ = config.voxelZOf(ground)
        for (globalZ in bottomZ..minOf(topZ, surfaceZ - 1)) {
          if (voxelAt(centreVoxelX + dx, voxelY, globalZ) == BlockType.AIR) voidVoxels++
        }
      }
      println()
      println("air below the ground surface: $voidVoxels voxels")
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

      // Descent per station-to-station segment, in metres per kilometre. Signed, so an uphill segment
      // shows up as a negative number rather than being hidden inside an absolute value.
      val gradients = ArrayList<Double>()
      var uphillSegments = 0
      var worstUphill = 0.0
      var flatMetres = 0.0
      var totalMetres = 0.0

      generated.world.features.all().filterIsInstance<PolylineFeature>()
        .filter { it.kind.name == "RIVER_CHANNEL" }
        .forEach { river ->
          val widthChannel = river.stations.channel(Profiles.CHANNEL_WIDTH)
          val depthChannel = river.stations.channel(Profiles.CHANNEL_DEPTH)
          val bedChannel = river.stations.channel(Profiles.CHANNEL_BED_ELEVATION)
          for (station in 0 until river.stations.stationCount) {
            widths.add(river.stations.valueAt(widthChannel, station))
            depths.add(river.stations.valueAt(depthChannel, station))
          }

          val line = river.centerline
          for (station in 0 until river.stations.stationCount - 1) {
            val run = line.arcLengthAt(station + 1) - line.arcLengthAt(station)
            if (run <= 0.0) continue
            val drop = river.stations.valueAt(bedChannel, station) -
                river.stations.valueAt(bedChannel, station + 1)
            val gradient = drop / run * 1000.0

            gradients.add(gradient)
            totalMetres += run
            if (gradient < 0.0) {
              uphillSegments++
              worstUphill = minOf(worstUphill, gradient)
            }
            // A metre per kilometre is already a lazy lowland river. Below a hundredth of that the water
            // surface does not cross a voxel boundary in a whole chunk, which is the canal look.
            if (gradient < DEAD_FLAT_PER_KM) flatMetres += run
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

      if (gradients.isEmpty()) return

      gradients.sort()
      println()
      println("bed gradient over ${gradients.size} segments, ${"%.1f".format(Locale.ROOT, totalMetres / 1000.0)} km of channel")
      println("                   min      p25      p50      p75      max")
      println("  m per km    ${quantiles(gradients)}")
      println()
      // The invariant. A river that climbs is the one hydrology failure a player can see from the bank,
      // so it is reported as a count rather than left to be inferred from a negative minimum.
      println("  flowing uphill           ${percent(uphillSegments, gradients.size)}" +
          if (uphillSegments > 0) ", worst ${"%.4f".format(Locale.ROOT, worstUphill)} m/km" else "")
      println("  below $DEAD_FLAT_PER_KM m/km (reads as a canal)  " +
          "${"%.1f".format(Locale.ROOT, 100.0 * flatMetres / totalMetres)}% of channel length")
    }

    /**
     * How much of the world reads as its runner-up biome, and how the confidence layer is distributed.
     *
     * The measurement `SurfaceSampler.biomeAt`'s tuning rests on and nothing could produce. Its KDoc records
     * that the confidence is 0.066 at the median over cells with a runner-up, which is why it is not usable as
     * a blend weight - but that figure was measured once, by hand, and there was no way to check it or to see
     * what the dither actually does to a world afterwards. A calibration pass that cannot re-measure its own
     * subject is a guess with a number in it.
     *
     * Three things, in the order the reasoning goes:
     *
     * 1. **How much of the world is even a candidate.** A cell with no runner-up never mixes, so the share of
     *    cells that have one bounds everything below.
     * 2. **The confidence distribution over those cells.** This is what the blend weight is computed from.
     * 3. **The area that actually comes out as the runner-up**, by asking [SurfaceSampler.biomeAt] itself on a
     *    lattice of world positions rather than by re-deriving the formula here. Re-deriving it would measure
     *    this method's copy of the arithmetic, which is worth nothing.
     *
     * The lattice spacing is deliberately coprime with both the kilometre cell and the 14 m patch wavelength.
     * An area fraction needs independent samples rather than dense ones, and a spacing that shares a factor
     * with either grid would sample the same phase of the noise every time and report a fraction that is an
     * artefact of the stride.
     */
    fun ecotone() {
      val biome = generated.world.layers.require<IntLayer>(LayerId.BIOME)
      val secondary = generated.world.layers.require<IntLayer>(LayerId.BIOME_SECONDARY)
      val confidence = generated.world.layers.require<FloatLayer>(LayerId.BIOME_CONFIDENCE)

      val withRunnerUp = ArrayList<Double>()
      var cells = 0
      for (i in secondary.data.indices) {
        cells++
        if (secondary.data[i] != LayerId.NO_SECONDARY) withRunnerUp.add(confidence.data[i].toDouble())
      }

      println()
      println("$cells coarse cells, ${percent(withRunnerUp.size, cells)} with a runner-up biome")

      if (withRunnerUp.isEmpty()) {
        println("nothing to dither, so nothing to measure")
        return
      }

      withRunnerUp.sort()
      println()
      println("BIOME_CONFIDENCE over the cells that have a runner-up")
      println("                   min      p25      p50      p75      max")
      println("  confidence  ${quantiles(withRunnerUp)}")
      println("  p95         ${"%9.3f".format(Locale.ROOT, withRunnerUp[(withRunnerUp.size - 1) * 95 / 100])}")

      val surface = generated.materializer.surface
      var sampled = 0
      var asRunnerUp = 0
      var candidates = 0

      var y = 0.0
      while (y < config.heightMetres) {
        var x = 0.0
        while (x < config.widthMetres) {
          val runnerUpOrdinal = secondary.sampleNearest(x, y)

          sampled++
          if (runnerUpOrdinal != LayerId.NO_SECONDARY) {
            candidates++
            val runnerUp = Biome.entries.getOrNull(runnerUpOrdinal)
            val winner = Biome.entries.getOrNull(biome.sampleNearest(x, y))
            if (runnerUp != null && runnerUp != winner && surface.biomeAt(x, y) == runnerUp) asRunnerUp++
          }
          x += LATTICE_METRES
        }
        y += LATTICE_METRES
      }

      println()
      println("$sampled world positions on a ${LATTICE_METRES.toInt()} m lattice")
      println("  reading as the runner-up, of the whole world      ${percent(asRunnerUp, sampled)}")
      println("  reading as the runner-up, of cells that have one  ${percent(asRunnerUp, candidates)}")
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
    /**
     * Every prop standing in the chunks around a point, by kind.
     *
     * The one human check on the prop tier, and it exists because the other views cannot be one: props are not
     * in the voxel grid, so `--section` through a forest prints bare grass and the plan view prints turf. A
     * world with no trees on it at all would look exactly like a world with trees until a client renders one.
     *
     * Prints a per-kind census over the window and then the individual props of the centre chunk, because the
     * two answer different questions - "does this world have vegetation" and "is that tree standing on the
     * ground".
     */
    fun props(nth: Int, onFeature: String?, atX: Double?, atY: Double?, span: Int) {
      val centre = when {
        onFeature != null -> midpointOf(onFeature, nth)
        atX != null && atY != null -> atX to atY
        else -> nearestLand(generated.world.layers.require<FloatLayer>(LayerId.ELEVATION), 400.0)
          .also { println("  no coordinate given, so: nearest land to 400 m is (${it.first.toInt()}, ${it.second.toInt()})") }
      }

      val extent = config.chunkExtent
      val centreChunkX = Math.floorDiv(Math.floor(centre.first / config.voxelSize).toLong(), config.chunkSize.toLong()).toInt()
      val centreChunkY = Math.floorDiv(Math.floor(centre.second / config.voxelSize).toLong(), config.chunkSize.toLong()).toInt()
      val reach = maxOf(1, Math.ceil(span * config.voxelSize / extent).toInt())

      val census = LinkedHashMap<PropKind, Int>()
      PropKind.entries.forEach { census[it] = 0 }
      var chunks = 0

      for (chunkY in centreChunkY - reach..centreChunkY + reach) {
        for (chunkX in centreChunkX - reach..centreChunkX + reach) {
          val props = generated.propsIn(chunkX, chunkY)
          chunks++
          for (i in props.indices) census[props.kindAt(i)] = census.getValue(props.kindAt(i)) + 1
        }
      }

      val area = chunks * extent * extent / 10_000.0
      println("props over $chunks chunks (${"%.2f".format(Locale.ROOT, area)} ha) around (${centre.first.toInt()}, ${centre.second.toInt()}):")
      for ((kind, count) in census) {
        println("  ${kind.name.lowercase().padEnd(14)} $count  (${"%.1f".format(Locale.ROOT, count / area)}/ha)")
      }

      val here = generated.propsIn(centreChunkX, centreChunkY)
      println("chunk ($centreChunkX,$centreChunkY) holds ${here.count}:")
      for (i in here.indices) {
        val flags = buildString {
          if (here.isBlighted(i)) append(" blighted")
          if (here.isLarge(i)) append(" large")
        }
        println(
          "  ${here.kindAt(i).name.lowercase().padEnd(14)} at " +
              "(${"%.1f".format(Locale.ROOT, here.xAt(i))}, ${"%.1f".format(Locale.ROOT, here.yAt(i))}) " +
              "ground ${"%.2f".format(Locale.ROOT, here.groundAt(i))} m, " +
              "height ${"%.1f".format(Locale.ROOT, here.heightAt(i))} m" +
              (if (here.radiusAt(i) > 0.0) ", crown ${"%.1f".format(Locale.ROOT, here.radiusAt(i))} m" else "") +
              flags
        )
      }
    }

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
      //
      // A MarkerFeature is the same hazard and worse. It is pure geometry with `corridorWidthMax = 0`, so its
      // bbox is exactly the extent its centreline wanders over and nothing brings the centre back onto the
      // line: a wall circuit's bbox centre is the middle of the town, and a passage that turns as it goes has a
      // bbox centre in solid rock. Both would be "no such feature here" reported as a place to stand.
      // An AreaFeature is the third case and the same hazard again. A pond following a valley is a curved
      // ribbon whose bbox centre is up the hillside beside it, so probing there reports dry ground and reads
      // as "the pond is not in the voxels". Its area centroid is on the water for every shape emitted today;
      // a crescent's would not be, which is why this says which point it used rather than implying one.
      val line = when (feature) {
        is PolylineFeature -> feature.centerline
        is MarkerFeature -> feature.centerline
        else -> null
      }

      val at = when {
        line != null -> {
          val mid = line.pointAt(line.length / 2.0)
          println("  on ${feature.kind} ${nth + 1} of ${matching.size}, " +
              "midpoint of a ${line.length.toInt()} m centreline")
          mid.x to mid.y
        }

        feature is AreaFeature -> {
          val centroid = feature.ring.centroid
          val inside = if (feature.contains(centroid.x, centroid.y)) "inside" else "OUTSIDE the ring"
          println("  on ${feature.kind} ${nth + 1} of ${matching.size}, " +
              "centroid of a ${feature.ring.area.toInt()} m2 ring ($inside)")
          centroid.x to centroid.y
        }

        else -> {
          val box = feature.bbox
          println("  on ${feature.kind} ${nth + 1} of ${matching.size}, centre of its bounds")
          ((box.minX + box.maxX) / 2.0) to ((box.minY + box.maxY) / 2.0)
        }
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
          sampled.add(Triple(x to y, window(x, y, span), Biome.entries[biome.sampleNearest(x, y)]))
        }
      }

      println()
      println("surveyed ${sampled.size} land patches of ${span}x${span} m")

      // Stems per hectare, which `VegetationScatter`'s KDoc has claimed this task reports since it was
      // written and which it did not: grep for it found nothing. It is the figure the densities in
      // `VegetationParams` are quoted in, so without it those numbers cannot be checked against a world.
      //
      // Counted as *props*, not as trunks in the voxels: the props are what a runtime is handed, so they are
      // what a tuning decision is about. Over the same strided sample, so it costs one prop pass per patch.
      var stems = 0
      var hectares = 0.0
      for ((at, _, _) in sampled) {
        val chunkX = Math.floorDiv(Math.floor(at.first / config.voxelSize).toLong(), config.chunkSize.toLong()).toInt()
        val chunkY = Math.floorDiv(Math.floor(at.second / config.voxelSize).toLong(), config.chunkSize.toLong()).toInt()
        val props = generated.propsIn(chunkX, chunkY)
        for (i in props.indices) if (props.kindAt(i) == PropKind.TREE) stems++
        hectares += config.chunkExtent * config.chunkExtent / 10_000.0
      }
      if (hectares > 0.0) {
        println(
          "tree props: $stems over ${"%.1f".format(Locale.ROOT, hectares)} ha sampled, " +
              "${"%.1f".format(Locale.ROOT, stems / hectares)} per hectare"
        )
      }

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

    /**
     * The distribution of the voxel-scale surface gradient over dry land.
     *
     * This is the measurement `ChunkMaterializer.BARE_ROCK_GRADIENT` has to be chosen from, and it cannot be
     * reasoned about from the detail parameters. The gradient a column reports is the sum of five broad-detail
     * octaves, four ridge octaves and every vector feature that reaches it, over a one-voxel baseline - so its
     * upper tail is an emergent property of the whole chunk tier, not a number anybody can derive.
     *
     * Prints a survival curve rather than a mean, because the threshold is a quantile question: what is wanted
     * is "the steepest one or two per cent of the world", and the mean says nothing about where that lands. The
     * failure mode a mean also hides is the one worth watching for - if the curve has no knee, the threshold is
     * inside the noise floor and picking any value from it selects noise rather than cliffs.
     *
     * Samples the same strided land cells [survey] does, and for the same reason.
     */
    fun steepness(elevation: FloatLayer, span: Int) {
      val samples = ArrayList<Double>()
      var visited = 0

      forEachLandCell(elevation) { x, y, _ ->
        if (visited++ % STRIDE == 0) {
          val half = span / 2
          for (dy in -half..half) {
            for (dx in -half..half) {
              val gradient = gradientAt(
                x + dx * config.voxelSize,
                y + dy * config.voxelSize
              )
              if (!gradient.isNaN()) samples.add(gradient)
            }
          }
        }
      }

      if (samples.isEmpty()) {
        println("no land columns sampled")
        return
      }

      samples.sort()
      println()
      println("voxel-scale surface gradient over ${samples.size} dry land columns")
      println("  median ${"%.3f".format(Locale.ROOT, quantile(samples, 0.5))}")
      println()
      println("  share of land at or above a candidate BARE_ROCK_GRADIENT:")
      for (threshold in CANDIDATE_GRADIENTS) {
        val above = samples.count { it >= threshold }
        println(
          "    %.2f  %6.3f%%   (%.0f degrees)".format(
            Locale.ROOT, threshold, 100.0 * above / samples.size,
            Math.toDegrees(Math.atan(threshold))
          )
        )
      }
    }

    /** Central difference over one voxel, the same construction `ChunkMaterializer.gradientAt` uses. */
    private fun gradientAt(worldX: Double, worldY: Double): Double {
      val step = config.voxelSize
      val dx = (terrainAtWorld(worldX + step, worldY) - terrainAtWorld(worldX - step, worldY)) / (2.0 * step)
      val dy = (terrainAtWorld(worldX, worldY + step) - terrainAtWorld(worldX, worldY - step)) / (2.0 * step)
      return Math.sqrt(dx * dx + dy * dy)
    }

    private fun terrainAtWorld(worldX: Double, worldY: Double): Double = terrainAt(
      Math.floor(worldX / config.voxelSize).toInt(),
      Math.floor(worldY / config.voxelSize).toInt()
    )

    private fun quantile(sorted: List<Double>, at: Double): Double =
      sorted[((sorted.size - 1) * at).toInt()]

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

  /**
   * Bed gradient in metres per kilometre below which a channel reads as a canal rather than as a river.
   *
   * A hundredth of a metre per kilometre is half a millimetre over the 48 m the probe prints and under a
   * voxel over a whole chunk, so the water surface is exactly level everywhere a player can see at once.
   * Real lowland rivers run one to two orders of magnitude steeper than this; anything under it is the
   * Priority-Flood epsilon showing through rather than a gentle river.
   */
  private const val DEAD_FLAT_PER_KM = 0.01

  /** Distinct at a glance in a terminal, quietest glyph first so the commonest material recedes. */
  private const val GLYPHS = ".:oO#*+=%@$&~"

  /** What to look at, as opposed to which world to look at it in - see [WorldArgs]. */
  private const val DROPLETS = "--droplets"

  private const val ECOTONE = "--ecotone"

  private const val STEEPNESS = "--steepness"

  /** Candidate thresholds for `ChunkMaterializer.BARE_ROCK_GRADIENT`, from a gentle bank to a sheer face. */
  private val CANDIDATE_GRADIENTS = doubleArrayOf(0.3, 0.4, 0.5, 0.6, 0.7, 0.85, 1.0, 1.25, 1.5)

  /**
   * The only way a human can see whether a world has trees on it.
   *
   * Not optional tooling. Once vegetation is emitted as props rather than written into voxels, `--section` and
   * the plan view show a forest as bare grass - the trees are simply not in the voxel grid any more - so
   * without this there is no check between the scatter and a client that has not been written yet.
   */
  private const val PROPS = "--props"

  private const val SECTION = "--section"

  /**
   * Metres of ground to print below the lowest column of a section.
   *
   * Spelled `--below` and reached by `-Pbelow` rather than the obvious `--depth`, because `-Pdepth` cannot be
   * read from a Gradle build: `Project.getDepth()` exists, so `project.hasProperty('depth')` is true on every
   * build and the value forwarded is the project's nesting level. It arrived here as `1` no matter what was
   * asked for, and the section quietly printed a metre of rock. See the `cli` helper in `worldgen/build.gradle`.
   */
  private const val BELOW = "--below"

  /** Voxels of sky above the highest *thing* in a section, so an open shaft has somewhere to open into. */
  private const val SECTION_SKY = 2

  /**
   * How far above the ground a section looks for something standing on it, in voxels.
   *
   * Comfortably over the tallest tree the scatter draws and the tallest tower the town stage builds. Costs one
   * column scan of already-materialised slabs, and the alternative is a picture with the interesting half of
   * its subject cropped off the top.
   */
  private const val SECTION_SCAN = 44

  /**
   * Spacing of the ecotone lattice in metres.
   *
   * Coprime with the 1000 m cell and not a multiple of the 14 m patch wavelength, so consecutive samples land on
   * unrelated phases of the noise. 211 m over a 128 km world is about 370 000 samples, which is a second of work
   * and a standard error on a few-percent fraction of well under a tenth of a point.
   */
  private const val LATTICE_METRES = 211.0

  private val PROBE_FLAGS = setOf(
    "--x", "--y", "--span", "--at", "--survey", "--on", "--nth", "--channels", BELOW,
    DROPLETS, ECOTONE, PROPS, SECTION, STEEPNESS
  )
}
