package net.bestia.zone.cartography.tools

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.place.PlaceRegions
import net.bestia.worldgen.render.Viewport
import net.bestia.worldgen.render.optionalAttribute
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.cartography.render.AtlasPalette
import net.bestia.zone.cartography.render.MapVisibility
import net.bestia.zone.cartography.render.TerrainRaster
import net.bestia.zone.cartography.render.TileInputs
import java.util.Locale
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow

/**
 * What the map thinks is at a place, as text.
 *
 * The counterpart to `mapRender`: that shows what the style drew, this shows what it was drawing *from*. The
 * two questions a wrong-looking map raises are "is the picture wrong" and "is the data wrong", and a
 * renderer can only ever answer the first.
 *
 * ```
 * ./gradlew :zone-server:mapInspect -Pgenesis -Pcoast          # find coastlines to look at
 * ./gradlew :zone-server:mapInspect -Pgenesis -Px=36864 -Py=70848
 * ./gradlew :zone-server:mapInspect -Pgenesis -Px=36864 -Py=70848 -Pto=40000,70848 -Plevel=7
 * ```
 */
object MapInspectMain {

  private const val X = "--x"
  private const val Y = "--y"
  private const val TO = "--to"
  private const val STEPS = "--steps"
  private const val LEVEL = "--level"
  private const val COAST = "--coast"
  private const val GRID = "--grid"
  private const val CENSUS = "--census"
  private const val TOWNS = "--towns"

  private val FLAGS = setOf(X, Y, TO, STEPS, LEVEL, COAST, GRID, CENSUS, TOWNS)

  @JvmStatic
  fun main(argv: Array<String>) {
    val args = MapToolArgs.parse(argv, FLAGS)
    val config = args.config
    val generated = StandardWorld.build(config, params = args.params)
    val inputs = TileInputs.of(generated)

    println(
      "world %.0f x %.0f km, %.0f m/cell, seed %d, sea level %.1f m".format(
        Locale.ROOT, config.widthMetres / 1000.0, config.heightMetres / 1000.0,
        config.baseResolution.metresPerCell, config.seed, config.seaLevel
      )
    )

    if (args.has(CENSUS)) {
      reportCensus(generated, config.widthMetres, config.heightMetres)
      return
    }

    if (args.has(TOWNS)) {
      reportTowns(inputs)
      return
    }

    if (args.has(COAST)) {
      reportCoasts(inputs, config.widthMetres, config.heightMetres)
      return
    }

    val x = args.double(X, config.widthMetres / 2.0)
    val y = args.double(Y, config.heightMetres / 2.0)
    val to = args.string(TO)

    if (args.has(GRID)) {
      reportGrid(inputs, x, y, args.int(STEPS, 12))
      return
    }

    if (to == null) {
      reportPoint(generated, inputs, x, y)
    } else {
      val (toX, toY) = to.split(',').let { it[0].trim().toDouble() to it[1].trim().toDouble() }
      reportTransect(inputs, x, y, toX, toY, args.int(STEPS, 40), args.int(LEVEL, 7))
    }
  }

  /**
   * Every settlement with its name and coordinates, largest first: where to point `mapRender`.
   *
   * The plan style only has anything to draw inside a town, so "render a town" needs a coordinate, and reading
   * one off a rendered world map by counting pixels is both tedious and, as it turned out, easy to get wrong by
   * a kilometre. This is the same two-sided join `PlaceInk` does to label a town, printed instead of drawn.
   */
  private fun reportTowns(inputs: TileInputs) {
    val towns = inputs.featuresIn(WHOLE_WORLD)
      .filterIsInstance<PointMarker>()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .mapNotNull { marker ->
        val index = marker.optionalAttribute(SettlementChannels.INDEX)?.toInt() ?: return@mapNotNull null
        val culture = marker.optionalAttribute(SettlementChannels.CULTURE)?.toInt() ?: return@mapNotNull null
        val tier = marker.optionalAttribute(SettlementChannels.TIER)?.toInt()
          ?.let { SettlementTier.entries.getOrNull(it) } ?: return@mapNotNull null
        val record = inputs.chronicle.settlements.getOrNull(index)

        Town(
          name = record?.takeIf { it.nameSeed != 0L }?.let { Names.place(it.nameSeed, culture) } ?: "(unnamed)",
          tier = tier,
          population = record?.population ?: 0,
          x = marker.position.x,
          y = marker.position.y
        )
      }
      .sortedByDescending { it.population }

    println()
    println("%-22s %-9s %8s %9s %9s".format(Locale.ROOT, "name", "tier", "people", "x", "y"))
    for (town in towns) {
      println(
        "%-22s %-9s %8d %9.0f %9.0f".format(
          Locale.ROOT, town.name, town.tier.label, town.population, town.x, town.y
        )
      )
    }
    println()
    println("${towns.size} settlements; render one with -Px=<x> -Py=<y> -Plevel=1")
  }

  private class Town(
    val name: String,
    val tier: SettlementTier,
    val population: Int,
    val x: Double,
    val y: Double
  )

  /**
   * Every feature kind the world contains, with what the map is allowed to do with it.
   *
   * Two questions at once. "Why is there nothing on my map" is usually answered by a count of zero rather than
   * by anything in the renderer - a 128 km world has few rivers and no cities, and no amount of styling will
   * draw what the generator did not produce. And reading the `visibility` column down the page is the check
   * that nothing a player should have to find by going there has been classified as drawable.
   */
  private fun reportCensus(generated: GeneratedWorld, widthMetres: Double, heightMetres: Double) {
    val counts = generated.world.features.all().groupingBy { it.kind }.eachCount()

    println()
    println("features in a %.0f x %.0f km world".format(Locale.ROOT, widthMetres / 1000.0, heightMetres / 1000.0))
    println("%-22s %8s  %-12s %s".format(Locale.ROOT, "kind", "count", "visibility", "drawn at or below"))

    for (kind in FeatureKind.entries) {
      val count = counts[kind] ?: 0
      val visibility = MapVisibility.of(kind)
      val reach = when {
        visibility == MapVisibility.SECRET || visibility == MapVisibility.OMITTED -> "never"
        visibility.minMetresPerPixel == Double.MAX_VALUE -> "every zoom"
        else -> "%.0f m/px".format(Locale.ROOT, visibility.minMetresPerPixel)
      }

      println("%-22s %8d  %-12s %s".format(Locale.ROOT, kind.name, count, visibility.name, reach))
    }

    val drawable = counts.entries.filter { MapVisibility.draws(it.key, 1.0) }.sumOf { it.value }
    println()
    println("${counts.values.sum()} features, $drawable of them drawable at some zoom")
  }

  /**
   * Cells where land meets sea, so a coastline can be looked at without guessing at coordinates.
   *
   * Reported at cell resolution off the biome raster, which is the cheap way to *find* a coast. It is
   * deliberately not how the coast is *drawn* - see [TerrainRaster]'s note on why a per-cell classification
   * makes a staircase - but for "where should I point the renderer" a cell is plenty.
   */
  private fun reportCoasts(inputs: TileInputs, widthMetres: Double, heightMetres: Double) {
    val biome = inputs.biome
    val region = biome.region
    val metresPerCell = region.resolution.metresPerCell
    val found = ArrayList<String>()

    var cellY = region.minY
    while (cellY <= region.maxY && found.size < COASTS_REPORTED) {
      var cellX = region.minX
      while (cellX <= region.maxX && found.size < COASTS_REPORTED) {
        if (isCoastCell(inputs, cellX, cellY)) {
          val x = (cellX + 0.5) * metresPerCell
          val y = (cellY + 0.5) * metresPerCell
          found += "  %8.0f %8.0f   %s".format(
            Locale.ROOT, x, y, biomeAt(inputs, x, y)?.label ?: "outside"
          )
        }
        cellX += COAST_SCAN_STRIDE
      }
      cellY += COAST_SCAN_STRIDE
    }

    println("coastal cells (land with an ocean neighbour), every ${COAST_SCAN_STRIDE}th cell scanned:")
    println("         x        y   biome")
    found.forEach(::println)
    println("world spans 0..%.0f by 0..%.0f m".format(Locale.ROOT, widthMetres, heightMetres))
  }

  private fun isCoastCell(inputs: TileInputs, cellX: Int, cellY: Int): Boolean {
    val biome = inputs.biome
    val here = Biome.entries.getOrNull(biome[cellX, cellY]) ?: return false
    if (here == Biome.OCEAN) return false

    for (dy in -1..1) {
      for (dx in -1..1) {
        if (!biome.region.contains(cellX + dx, cellY + dy)) continue
        if (Biome.entries.getOrNull(biome[cellX + dx, cellY + dy]) == Biome.OCEAN) return true
      }
    }

    return false
  }

  /**
   * Raw elevation cells around a point, as stored, with no interpolation anywhere in the path.
   *
   * The question this answers is whether a stepped-looking coastline is stepped in the *data*. An
   * interpolated read cannot answer it: bicubic over a terraced grid and bicubic over a smooth one both come
   * back smooth to any single sample, and the difference only shows in the shape of a contour over many
   * cells. Sign is printed beside the value because that is all the shoreline depends on.
   */
  private fun reportGrid(inputs: TileInputs, x: Double, y: Double, span: Int) {
    val layer = inputs.elevation
    val metresPerCell = layer.region.resolution.metresPerCell
    val centreX = floor(x / metresPerCell).toInt()
    val centreY = floor(y / metresPerCell).toInt()
    val half = span / 2

    println()
    println(
      "raw elevation cells around cell %d,%d - '.' is sea, '#' is land, %.0f m per cell".format(
        Locale.ROOT, centreX, centreY, metresPerCell
      )
    )

    // North at the top, so the dump reads the same way round as the rendered map does.
    for (cellY in centreY + half downTo centreY - half) {
      val heights = StringBuilder()
      val signs = StringBuilder()

      for (cellX in centreX - half..centreX + half) {
        if (!layer.region.contains(cellX, cellY)) {
          heights.append("      -")
          signs.append(' ')
          continue
        }

        val h = layer[cellX, cellY].toDouble()
        heights.append("%7.0f".format(Locale.ROOT, h))
        signs.append(if (h < inputs.seaLevel) '.' else '#')
      }

      println("%5d %s   %s".format(Locale.ROOT, cellY, signs, heights))
    }
  }

  private fun reportPoint(generated: GeneratedWorld, inputs: TileInputs, x: Double, y: Double) {
    val metresPerCell = inputs.elevation.region.resolution.metresPerCell
    println()
    println("at %.0f, %.0f  (cell %d, %d)".format(
      Locale.ROOT, x, y, floor(x / metresPerCell).toInt(), floor(y / metresPerCell).toInt()
    ))
    println("  ground bicubic  %10.2f m".format(Locale.ROOT, inputs.elevation.sampleBicubic(x, y)))
    println("  base height     %10.2f m".format(Locale.ROOT, inputs.baseHeight.heightAt(x, y)))
    println("  water level     %10s".format(Locale.ROOT, waterLevelText(inputs, x, y)))
    println("  biome           %10s".format(Locale.ROOT, biomeAt(inputs, x, y)?.label ?: "outside"))
    println("  place           %10s".format(Locale.ROOT, placeAt(generated, x, y)))
    println("  canopy cover    %10.2f".format(Locale.ROOT, inputs.canopyCover.sampleBilinear(x, y)))
    println("  discharge       %10.2f m3/s".format(Locale.ROOT, inputs.discharge.sampleBilinear(x, y)))

    val near = inputs.featuresIn(Aabb(x - FEATURE_RADIUS, y - FEATURE_RADIUS, x + FEATURE_RADIUS, y + FEATURE_RADIUS))
    println("  features within %.0f m: %d".format(Locale.ROOT, FEATURE_RADIUS, near.size))
    near.groupingBy { it.kind }.eachCount().entries
      .sortedByDescending { it.value }
      .take(FEATURE_KINDS_REPORTED)
      .forEach { println("    %-22s %d".format(Locale.ROOT, it.key.name, it.value)) }
  }

  /**
   * The fields the renderer actually reads, sampled along a line at one zoom's pixel spacing.
   *
   * `shore` is the column to watch: it is what the coastline and the land-water fill both come off, so a
   * staircase in the picture is a staircase in this column or it is a bug in the drawing.
   */
  private fun reportTransect(
    inputs: TileInputs,
    fromX: Double,
    fromY: Double,
    toX: Double,
    toY: Double,
    steps: Int,
    level: Int
  ) {
    val metresPerPixel = 2.0.pow(level)
    val length = hypot(toX - fromX, toY - fromY)

    // One-pixel-tall viewport along the transect is not expressible, so sample the raster the same way the
    // style does - through TerrainRaster - and walk it. That way this reports the renderer's own numbers
    // rather than a second implementation of them that could disagree.
    println()
    println(
      "transect %.0f,%.0f -> %.0f,%.0f  %.0f m at L%d (%.1f m/px)".format(
        Locale.ROOT, fromX, fromY, toX, toY, length, level, metresPerPixel
      )
    )
    println("      x        y     ground      shore   wet  biome")

    for (s in 0..steps) {
      val t = if (steps == 0) 0.0 else s.toDouble() / steps
      val x = fromX + (toX - fromX) * t
      val y = fromY + (toY - fromY) * t

      val view = Viewport(x, y, metresPerPixel, 1, 1)
      val raster = TerrainRaster.sample(view, inputs, AtlasPalette.PARCHMENT)
      val i = raster.index(0, 0)

      println(
        "%7.0f %8.0f %10.2f %10.3f %5s  %s".format(
          Locale.ROOT, x, y, raster.ground[i], raster.shore[i],
          if (raster.shore[i] > 0.0) "wet" else "dry",
          biomeAt(inputs, x, y)?.label ?: "-"
        )
      )
    }
  }

  /**
   * The name a player standing here would read, and the region facts behind it.
   *
   * The partition is built here rather than taken off [GeneratedWorld] because it is not part of the
   * pipeline - see `place/PlaceRegions`. Cheap next to the world build this tool already paid for.
   */
  private fun placeAt(generated: GeneratedWorld, x: Double, y: Double): String {
    val regions = PlaceRegions.of(generated.world)
    val region = regions.regionAt(x, y)

    val squareKm = region.cellCount * regions.cellSize * regions.cellSize / 1_000_000.0

    return "%s  (%s, %.0f km2, relief %.0f m, of %d regions)".format(
      Locale.ROOT, region.name, region.kind.name, squareKm, region.relief, regions.count
    )
  }

  private fun waterLevelText(inputs: TileInputs, x: Double, y: Double): String {
    val layer = inputs.waterLevel
    val metresPerCell = layer.region.resolution.metresPerCell
    val cellX = floor(x / metresPerCell).toInt()
    val cellY = floor(y / metresPerCell).toInt()

    if (!layer.region.contains(cellX, cellY)) return "outside"
    val level = layer[cellX, cellY].toDouble()
    return if (level.isNaN()) "dry" else "%.2f m".format(Locale.ROOT, level)
  }

  private fun biomeAt(inputs: TileInputs, x: Double, y: Double): Biome? {
    val layer = inputs.biome
    val metresPerCell = layer.region.resolution.metresPerCell
    val cellX = floor(x / metresPerCell).toInt()
    val cellY = floor(y / metresPerCell).toInt()

    if (!layer.region.contains(cellX, cellY)) return null
    return Biome.entries.getOrNull(layer[cellX, cellY])
  }

  private const val COASTS_REPORTED = 24
  private const val COAST_SCAN_STRIDE = 7
  private const val FEATURE_RADIUS = 1500.0
  private const val FEATURE_KINDS_REPORTED = 8

  /** Every feature, for the settlement listing. Cheap enough once: the store is a frozen index. */
  private val WHOLE_WORLD = Aabb(-1e9, -1e9, 1e9, 1e9)

}
