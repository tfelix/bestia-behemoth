package net.bestia.worldgen.civ

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureEvaluator
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Vec2d
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Measures how far the sea could rise in a production-size world before it drowns the start towns or too much land.
 * It answers the Doomsday Flood study and asserts nothing.
 *
 * Run it alone, it builds 35 worlds in about two minutes:
 * `FLOOD_STUDY=1 ./gradlew :worldgen:test --tests net.bestia.worldgen.civ.FloodStudyReport --rerun`.
 * The report is in the test's system-out, under `worldgen/build/test-results/test`.
 */
@EnabledIfEnvironmentVariable(named = "FLOOD_STUDY", matches = "1")
class FloodStudyReport {

  @Test
  fun `report flood headroom per world`() {
    val worlds = TestWorlds.sweep(SEEDS.map(::productionConfig)) { measure(it) }
    println(render(worlds))
  }

  private class Town(
    val name: String,
    val floor: Double,
    val lowEdge: Double,
    val spawn: Double,
    val spawnAt: Vec2d
  ) {
    /** A town is dry while the water stays below both its built ground and the spot a master arrives at. */
    val safeHeight: Double get() = min(lowEdge, spawn)
  }

  /** [keepStartTown] false lets the water stop at the landmass floor alone, to see what a full flood leaves. */
  private class Scenario(val config: Double, val landmassFloor: Double, val keepStartTown: Boolean = true)

  private class WorldHeadroom(
    val seed: Long,
    val landKm2: Double,
    val startTowns: List<Town>,
    val homes: List<Town>,
    val settlementLowEdges: List<Double>,
    val grid: SpillGrid
  ) {
    /** Per land sample: the lowest sea rise that reaches it over lower ground. Sorted. */
    private val landSpill = grid.landSpill()

    fun dryLandShare(rise: Double): Double {
      val flooded = upperBound(landSpill, rise)
      return 1.0 - flooded.toDouble() / landSpill.size
    }

    /** The highest rise that still leaves [share] of the land dry. */
    fun riseKeepingDry(share: Double): Double {
      val index = ((1.0 - share) * landSpill.size).toInt().coerceIn(0, landSpill.size - 1)
      return landSpill[index]
    }

    val highestStartTown: Double get() = startTowns.maxOfOrNull { it.safeHeight } ?: Double.NEGATIVE_INFINITY

    val highestStartTownSpawn: Vec2d get() = startTowns.maxBy { it.safeHeight }.spawnAt

    val highestHome: Town get() = homes.maxBy { it.safeHeight }
  }

  /** A 250 m grid of ground height and spill height: the lowest sea rise that reaches a sample over lower ground. */
  private class SpillGrid(val width: Int, val height: Int, val ground: DoubleArray, val spill: DoubleArray) {

    fun landSpill(): DoubleArray {
      return ground.indices.filter { ground[it] > 0.0 }.map { spill[it] }.sorted().toDoubleArray()
    }

    /** The dry islands left at [rise]: an island id per sample (-1 under water) and the size of each island. */
    fun islands(rise: Double): Pair<IntArray, List<Int>> {
      val label = IntArray(spill.size) { -1 }
      val sizes = ArrayList<Int>()
      val stack = ArrayDeque<Int>()
      for (start in spill.indices) {
        if (label[start] != -1 || spill[start] <= rise) continue
        val id = sizes.size
        var size = 0
        label[start] = id
        stack.addLast(start)
        while (stack.isNotEmpty()) {
          val i = stack.removeLast()
          size++
          for ((dx, dy) in NEIGHBOURS) {
            val nx = i % width + dx
            val ny = i / width + dy
            if (nx !in 0 until width || ny !in 0 until height) continue
            val n = ny * width + nx
            if (label[n] != -1 || spill[n] <= rise) continue
            label[n] = id
            stack.addLast(n)
          }
        }
        sizes.add(size)
      }
      return label to sizes
    }

    fun indexOf(at: Vec2d): Int {
      val x = (at.x / GRID_STEP).toInt().coerceIn(0, width - 1)
      val y = (at.y / GRID_STEP).toInt().coerceIn(0, height - 1)
      return y * width + x
    }
  }

  private fun measure(generated: GeneratedWorld): WorldHeadroom {
    val homes = SettlementSpawnPoints.choose(generated, HOMES_HELD)
    val sites = settlementSites(generated)

    fun townOf(candidate: SettlementSpawnPoints.Candidate): Town {
      val site = sites.getValue(candidate.settlementIndex)
      val ground = groundSamples(generated, site)
      return Town(
        name = candidate.name,
        floor = percentile(ground, 0.5),
        lowEdge = percentile(ground, 0.1),
        spawn = generated.base.heightAt(candidate.position.x, candidate.position.y) - generated.config.seaLevel,
        spawnAt = candidate.position
      )
    }

    val chronicle = generated.world.chronicle
    val standing = sites.filterKeys { chronicle.settlementStood(it, chronicle.presentYear) }.values
    val grid = spillGrid(generated)

    return WorldHeadroom(
      seed = generated.config.seed,
      landKm2 = grid.ground.count { it > 0.0 } * CELL_KM2,
      startTowns = homes.take(START_TOWNS).map(::townOf),
      homes = homes.map(::townOf),
      settlementLowEdges = standing.map { percentile(groundSamples(generated, it), 0.1) },
      grid = grid
    )
  }

  private fun settlementSites(generated: GeneratedWorld): Map<Int, PointMarker> {
    return generated.world.features.all()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
      .associateBy { it.attribute(SettlementChannels.INDEX).toInt() }
  }

  /**
   * Finished ground height above sea level, rivers and grading included, over the levelled core of a settlement.
   * The core is the inner part of the grading disc, where the terrace is flat and the buildings stand.
   */
  private fun groundSamples(generated: GeneratedWorld, site: PointMarker): List<Double> {
    val tier = SettlementTier.entries[site.attribute(SettlementChannels.TIER).toInt()]
    val core = tier.footprintRadius * GRADED_CORE
    val centre = site.position
    val evaluator = FeatureEvaluator(
      generated.world.features.query(Aabb(centre.x - core, centre.y - core, centre.x + core, centre.y + core))
    )

    val points = listOf(centre) + (1..RINGS).flatMap { ring ->
      val radius = core * ring / RINGS
      (0 until BEARINGS).map { b ->
        val angle = 2.0 * Math.PI * b / BEARINGS
        Vec2d(centre.x + cos(angle) * radius, centre.y + sin(angle) * radius)
      }
    }

    return points.map { evaluator.heightAt(it.x, it.y, generated.base.heightAt(it.x, it.y)) - generated.config.seaLevel }
  }

  /**
   * How high the sea must rise to reach each sample from the open sea over lower ground.
   * Sea water stays connected, so a dry basin behind a ridge is not flooded until the water tops the ridge.
   * This is a priority flood seeded from the map border, which `OceanBorder` keeps as open sea.
   */
  private fun spillGrid(generated: GeneratedWorld): SpillGrid {
    val config = generated.config
    val width = (config.widthMetres / GRID_STEP).toInt()
    val height = (config.heightMetres / GRID_STEP).toInt()
    val ground = DoubleArray(width * height) { i ->
      val x = (i % width + 0.5) * GRID_STEP
      val y = (i / width + 0.5) * GRID_STEP
      generated.base.heightAt(x, y) - config.seaLevel
    }

    val spill = DoubleArray(ground.size) { Double.NaN }
    val queue = PriorityQueue<Int>(compareBy { spill[it] })
    for (i in ground.indices) {
      val x = i % width
      val y = i / width
      if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
        spill[i] = ground[i]
        queue.add(i)
      }
    }

    while (queue.isNotEmpty()) {
      val i = queue.poll()
      val x = i % width
      val y = i / width
      for ((dx, dy) in NEIGHBOURS) {
        val nx = x + dx
        val ny = y + dy
        if (nx !in 0 until width || ny !in 0 until height) continue
        val n = ny * width + nx
        if (!spill[n].isNaN()) continue
        spill[n] = maxOf(ground[n], spill[i])
        queue.add(n)
      }
    }

    return SpillGrid(width, height, ground, spill)
  }

  private fun render(worlds: List<WorldHeadroom>): String {
    val out = StringBuilder()

    out.appendLine("## Per world (heights in m above sea level)")
    out.appendLine()
    out.appendLine("| seed | land km² | start towns: floor / low edge / spawn | highest safe start town | homes: min / median / max low edge | rise for 70% / 25% / 20% dry land |")
    out.appendLine("|---|---|---|---|---|---|")
    for (w in worlds) {
      val starts = w.startTowns.joinToString("<br>") { "${it.name} ${f(it.floor)} / ${f(it.lowEdge)} / ${f(it.spawn)}" }
      val homeLows = w.homes.map { it.lowEdge }
      out.appendLine(
        "| ${w.seed} | ${f(w.landKm2)} | $starts | ${f(w.highestStartTown)} | " +
            "${f(homeLows.minOrNull())} / ${f(percentile(homeLows, 0.5))} / ${f(homeLows.maxOrNull())} | " +
            "${LANDMASS_FLOORS.joinToString(" / ") { f(w.riseKeepingDry(it)) }} |"
      )
    }

    out.appendLine()
    out.appendLine("## Per rise h (over ${worlds.size} worlds)")
    out.appendLine()
    out.appendLine("| h | dry land mean | dry land min | settlements dry mean | homes dry mean | worlds without a dry start town | worlds without a dry home |")
    out.appendLine("|---|---|---|---|---|---|---|")
    for (h in RISES) {
      val dryLand = worlds.map { it.dryLandShare(h) }
      val settlementsDry = worlds.map { w -> w.settlementLowEdges.count { it > h }.toDouble() / w.settlementLowEdges.size }
      val homesDry = worlds.map { w -> w.homes.count { it.safeHeight > h }.toDouble() / w.homes.size }
      val noStartTown = worlds.count { w -> w.startTowns.none { it.safeHeight > h } }
      val noHome = worlds.count { w -> w.homes.none { it.safeHeight > h } }
      out.appendLine(
        "| $h | ${pct(dryLand.average())} | ${pct(dryLand.min())} | ${pct(settlementsDry.average())} | " +
            "${pct(homesDry.average())} | $noStartTown | $noHome |"
      )
    }

    out.appendLine()
    out.appendLine("Highest safe start town: min ${f(worlds.minOf { it.highestStartTown })}, " +
        "median ${f(percentile(worlds.map { it.highestStartTown }, 0.5))}. " +
        LANDMASS_FLOORS.joinToString(" ") { floor ->
          "Rise for ${pct(floor)} dry land: min ${f(worlds.minOf { it.riseKeepingDry(floor) })}, " +
              "median ${f(percentile(worlds.map { it.riseKeepingDry(floor) }, 0.5))}, " +
              "max ${f(worlds.maxOf { it.riseKeepingDry(floor) })}."
        })
    for (scenario in SCENARIOS) renderCaps(out, worlds, scenario)

    return out.toString()
  }

  private fun renderCaps(out: StringBuilder, worlds: List<WorldHeadroom>, scenario: Scenario) {
    val configText = if (scenario.config.isInfinite()) "no config cap" else "${f(scenario.config)} m"
    val startTownText = if (scenario.keepStartTown) "highest safe start town - $MARGIN, " else ""
    out.appendLine()
    out.appendLine(
      "## Per-world cap: maxRise = min($configText, ${startTownText}rise for ${pct(scenario.landmassFloor)} dry land)"
    )
    out.appendLine()
    out.appendLine(
      "| seed | start-town cap | land cap | maxRise | binding | fits (>= $MIN_RISE m) | dry land | " +
          "start town's island | highest home's island | largest island | islands >= ${ISLAND_KM2.toInt()} km² | " +
          "start towns drowned | homes drowned | settlements drowned |"
    )
    out.appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for (w in worlds) {
      val startCap = if (scenario.keepStartTown) w.highestStartTown - MARGIN else Double.POSITIVE_INFINITY
      val landCap = w.riseKeepingDry(scenario.landmassFloor)
      val maxRise = minOf(scenario.config, startCap, landCap)
      val binding = when (maxRise) {
        scenario.config -> "config"
        startCap -> "start town"
        else -> "land"
      }
      val (label, sizes) = w.grid.islands(maxRise)
      fun islandAt(at: Vec2d): Int = label[w.grid.indexOf(at)].let { if (it < 0) 0 else sizes[it] }
      val startIsland = if (w.highestStartTown > maxRise) islandAt(w.highestStartTownSpawn) else 0
      val homeIsland = if (w.highestHome.safeHeight > maxRise) islandAt(w.highestHome.spawnAt) else 0
      val bigIslands = sizes.count { it * CELL_KM2 >= ISLAND_KM2 }
      val startsDrowned = w.startTowns.count { it.safeHeight <= maxRise }
      val homesDrowned = w.homes.count { it.safeHeight <= maxRise }
      val settlementsDrowned = w.settlementLowEdges.count { it <= maxRise }
      out.appendLine(
        "| ${w.seed} | ${f(startCap)} | ${f(landCap)} | ${f(maxRise)} | $binding | ${maxRise >= MIN_RISE} | " +
            "${pct(w.dryLandShare(maxRise))} | ${f(startIsland * CELL_KM2)} km² | ${f(homeIsland * CELL_KM2)} km² | " +
            "${f((sizes.maxOrNull() ?: 0) * CELL_KM2)} km² | $bigIslands | " +
            "$startsDrowned / ${w.startTowns.size} | $homesDrowned / ${w.homes.size} | " +
            "$settlementsDrowned / ${w.settlementLowEdges.size} |"
      )
    }
  }

  private fun f(value: Double?): String {
    if (value == null) return "-"
    return "%.1f".format(Locale.ROOT, value)
  }

  private fun pct(share: Double): String {
    return "%.0f%%".format(Locale.ROOT, share * 100)
  }

  private fun percentile(values: List<Double>, share: Double): Double {
    if (values.isEmpty()) return Double.NaN
    val sorted = values.sorted()
    return sorted[(share * (sorted.size - 1)).toInt()]
  }

  private companion object {
    /** The seeds `TestWorlds` users build, which include the server's dev seed, and twenty more. */
    val SEEDS = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 10L, 11L, 42L, 909L, 0xC0FFEEL, StandardWorld.DEFAULT_SEED) +
        (2001L..2021L)

    /** The `worldgen.*` block of zone-server's application.yml. */
    fun productionConfig(seed: Long): WorldConfig {
      return WorldConfig(seed = seed, widthCells = 128, heightCells = 128, wrapX = true, wrapY = true)
    }

    // MasterSpawnPointService.HOMES_HELD and HOMES_OFFERED: zone-server is out of reach from here.
    const val HOMES_HELD = 12
    const val START_TOWNS = 3

    const val GRADED_CORE = 0.6
    const val RINGS = 4
    const val BEARINGS = 16
    const val GRID_STEP = 250.0
    val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    const val CELL_KM2 = GRID_STEP * GRID_STEP / 1e6
    const val ISLAND_KM2 = 10.0

    /** The study's 2 to 20 m, then the rises a world-ending flood needs. */
    val RISES = ((2..20 step 2) + listOf(30, 50, 75, 100, 150, 200, 300, 400, 500, 750, 1000, 1500)).map { it.toDouble() }
    const val MARGIN = 2.0
    const val MIN_RISE = 4.0
    val LANDMASS_FLOORS = listOf(0.7, 0.25, 0.2)

    val SCENARIOS = listOf(
      Scenario(20.0, 0.7),
      Scenario(100.0, 0.7),
      Scenario(Double.POSITIVE_INFINITY, 0.25),
      Scenario(Double.POSITIVE_INFINITY, 0.2),
      Scenario(Double.POSITIVE_INFINITY, 0.25, keepStartTown = false),
      Scenario(Double.POSITIVE_INFINITY, 0.2, keepStartTown = false)
    )

    fun upperBound(sorted: DoubleArray, value: Double): Int {
      var low = 0
      var high = sorted.size
      while (low < high) {
        val mid = (low + high) ushr 1
        if (sorted[mid] <= value) low = mid + 1 else high = mid
      }
      return low
    }
  }
}
