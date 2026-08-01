package net.bestia.worldgen.civ

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Timings
import net.bestia.worldgen.fields.DistanceTransform
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PolylineFeature
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * The individual habitability terms, precomputed per cell.
 *
 * Public within the module because [SettlementStage] scores the same terms with a different culture's
 * weights, and recomputing them there would be both wasteful and a place for the two to drift apart.
 */
internal class Terms(
  val region: CellRegion,
  val freshWater: Grid,
  val fertility: Grid,
  val arable: Grid,
  val defensibility: Grid,
  val resources: Grid,
  val climate: Grid,
  val harbour: Grid,
  val grazing: Grid,
  val hazard: Grid,
  val movementCost: Grid,
  /** True where the cell is under water, and therefore not somewhere anyone lives. */
  val submerged: BooleanArray
) {

  /** Weighted, normalised habitability in `[0,1]` for one culture. */
  fun scoreAt(index: Int, culture: Culture): Double {
    if (submerged[index]) return 0.0

    val weighted = culture.freshWater * freshWater.data[index] +
        culture.soilFertility * fertility.data[index] +
        culture.arableSlope * arable.data[index] +
        culture.defensibility * defensibility.data[index] +
        culture.resources * resources.data[index] +
        culture.climate * climate.data[index] +
        culture.harbour * harbour.data[index] +
        culture.grazing * grazing.data[index] -
        culture.hazardAversion * hazard.data[index]

    val total = culture.freshWater + culture.soilFertility + culture.arableSlope +
        culture.defensibility + culture.resources + culture.climate +
        culture.harbour + culture.grazing

    return (weighted / total).coerceIn(0.0, 1.0)
  }

  companion object {

    fun read(ctx: GenContext, region: CellRegion, params: HabitabilityParams): Terms {
      val metres = region.resolution.metresPerCell
      val seaLevel = ctx.config.seaLevel

      val elevation = Grid.from(ctx.layers.float(LayerId.ELEVATION))
      val fertility = Grid.from(ctx.layers.float(LayerId.SOIL_FERTILITY))
      val discharge = Grid.from(ctx.layers.float(LayerId.DISCHARGE))
      val waterLevel = Grid.from(ctx.layers.float(LayerId.WATER_LEVEL))
      val sediment = Grid.from(ctx.layers.float(LayerId.SEDIMENT))
      val resourceValue = Grid.from(ctx.layers.float(LayerId.RESOURCE_VALUE))
      val temperature = Grid.resampled(ctx.layers.float(LayerId.TEMPERATURE), region)
      val biome = ctx.layers.int(LayerId.BIOME)
      val lakeId = ctx.layers.int(LayerId.LAKE_ID)

      val cells = region.width * region.height
      val submerged = BooleanArray(cells) { !waterLevel.data[it].isNaN() }

      // Distance to fresh water from the river *polylines*, not from a discharge threshold on the grid. A
      // kilometre cell either has a river in it or does not; the polyline says how far away it actually is,
      // which is the difference between siting a village on a stream and siting it a kilometre from one.
      val toWater = Timings.measure("terms.freshWaterDistance") {
        freshWaterDistance(ctx, region, discharge, lakeId, params)
      }
      val toShore = Timings.measure("terms.shorelineDistance") {
        shorelineDistance(region, submerged, metres)
      }

      val freshWater = Timings.measure("terms.freshWater") {
        Grid.parallel(region.width, region.height) { x, y ->
          exp(-toWater.data[y * region.width + x] / params.waterRange)
        }
      }

      val arable = Timings.measure("terms.arable") { Grid.parallel(region.width, region.height) { x, y ->
        // Flat is good, but not swamp flat: standing water is not farmland.
        val slope = elevation.gradient(x, y, metres)
        val i = y * region.width + x
        val flatness = 1.0 - (slope / params.arableSlope).coerceIn(0.0, 1.0)
        val boggy = if (Biome.of(biome[region.minX + x, region.minY + y]) == Biome.WETLAND) 0.35 else 1.0
        flatness * boggy
      } }

      val defensibility = Timings.measure("terms.defensibility") {
        Grid.parallel(region.width, region.height) { x, y ->
          prominence(elevation, x, y, params.prominenceRadius, metres)
        }
      }

      val climate = Timings.measure("terms.climate") {
        Grid.parallel(region.width, region.height) { x, y ->
          val i = y * region.width + x
          val off = abs(temperature.data[i] - params.comfortTemperature)
          (1.0 - off / params.comfortTolerance).coerceIn(0.0, 1.0)
        }
      }

      // Harbour quality: sheltered water, which means a *concave* shoreline with deep water nearby. A
      // convex headland has the same distance to the sea and is a terrible place to keep a boat.
      val harbour = Timings.measure("terms.harbour") {
        harbourQuality(region, submerged, elevation, toShore, seaLevel, params)
      }

      val grazing = Timings.measure("terms.grazing") {
        Grid.parallel(region.width, region.height) { x, y ->
          when (Biome.of(biome[region.minX + x, region.minY + y])) {
            Biome.GRASSLAND, Biome.STEPPE -> 1.0
            Biome.SAVANNA, Biome.SHRUBLAND -> 0.7
            Biome.TUNDRA, Biome.ALPINE -> 0.4
            else -> 0.1
          }
        }
      }

      val hazard = Timings.measure("terms.hazard") { Grid.parallel(region.width, region.height) { x, y ->
        val i = y * region.width + x
        val above = elevation.data[i] - seaLevel
        // Floodplain: deep recent sediment barely above the water table. Where the crops are, and where the
        // village gets washed away every third generation - which is exactly the tension worth modelling.
        val flood = (sediment.data[i] / 8.0).coerceIn(0.0, 1.0) *
            (1.0 - (toWater.data[i] / params.waterRange).coerceIn(0.0, 1.0))
        val avalanche = (elevation.gradient(x, y, metres) / 0.3).coerceIn(0.0, 1.0)
        val storm = (1.0 - (above / 40.0).coerceIn(0.0, 1.0)) *
            (1.0 - (toShore.data[i] / 3_000.0).coerceIn(0.0, 1.0))
        max(flood, max(avalanche * 0.8, storm * 0.6))
      } }

      val movementCost = Timings.measure("terms.movementCost") {
        movementCost(region, elevation, discharge, waterLevel, biome, metres, params)
      }

      return Terms(
        region = region,
        freshWater = freshWater,
        fertility = fertility,
        arable = arable,
        defensibility = defensibility,
        resources = resourceValue,
        climate = climate,
        harbour = harbour,
        grazing = grazing,
        hazard = hazard,
        movementCost = movementCost,
        submerged = submerged
      )
    }

    /**
     * Metres to the nearest fresh water, measured against the river centerlines and lake cells.
     *
     * Rasterised at half-cell steps along each polyline so no crossed cell is missed, then a distance
     * transform. The transform is over cells, so the result is quantised to the grid - but the *source* is
     * the true geometry, which is what stops a river's influence from jumping about with the grid.
     */
    private fun freshWaterDistance(
      ctx: GenContext,
      region: CellRegion,
      discharge: Grid,
      lakeId: IntLayer,
      params: HabitabilityParams
    ): Grid {
      val metres = region.resolution.metresPerCell
      val isWater = BooleanArray(region.width * region.height)

      for (feature in ctx.features.query(region.toWorld())) {
        val river = feature as? PolylineFeature ?: continue
        if (river.kind != FeatureKind.RIVER_CHANNEL) continue

        var s = 0.0
        while (s <= river.centerline.length) {
          val point = river.centerline.pointAt(s)
          val x = (point.x / metres).toInt() - region.minX
          val y = (point.y / metres).toInt() - region.minY
          if (x in 0 until region.width && y in 0 until region.height) {
            isWater[y * region.width + x] = true
          }
          s += metres * 0.5
        }
      }

      for (y in 0 until region.height) {
        for (x in 0 until region.width) {
          val i = y * region.width + x
          // Lakes are fresh water too, and a river below the channel threshold still waters a hamlet.
          if (lakeId[region.minX + x, region.minY + y] > 0) isWater[i] = true
          if (discharge.data[i] >= params.waterDischarge) isWater[i] = true
        }
      }

      return DistanceTransform.euclideanMetres(region.width, region.height, metres) { x, y ->
        isWater[y * region.width + x]
      }.also { cap(it, region) }
    }

    private fun shorelineDistance(region: CellRegion, submerged: BooleanArray, metres: Double): Grid =
      DistanceTransform.euclideanMetres(region.width, region.height, metres) { x, y ->
        submerged[y * region.width + x]
      }.also { cap(it, region) }

    /**
     * Local elevation prominence in `[0,1]`: how much this cell stands above its surroundings.
     *
     * A hill in a plain scores high, a valley floor scores nothing, and a point halfway up a uniform slope
     * scores nothing either - which is right. Defensibility is about commanding the ground around you, and a
     * slope commands nothing.
     */
    private fun prominence(
      elevation: Grid,
      x: Int,
      y: Int,
      radius: Int,
      metresPerCell: Double
    ): Double {
      val here = elevation[x, y]
      var lower = 0
      var total = 0
      var relief = 0.0

      for (dy in -radius..radius) {
        for (dx in -radius..radius) {
          if (dx == 0 && dy == 0) continue
          val neighbour = elevation[x + dx, y + dy]
          total++
          if (neighbour < here) lower++
          relief = max(relief, here - neighbour)
        }
      }

      if (total == 0) return 0.0
      val surrounded = lower.toDouble() / total
      // Both matter: being higher than most of your neighbours, and by a militarily useful amount.
      val useful = (relief / (metresPerCell * 0.08)).coerceIn(0.0, 1.0)
      return surrounded * useful
    }

    /**
     * Harbour quality: shelter, which is concavity of the shoreline, times access to deep water.
     *
     * Concavity is measured as the fraction of the surrounding disc that is water. A cell at the head of an
     * inlet is surrounded by water on three sides and scores high; a cell on a headland is surrounded by
     * water on three sides *of a convex curve* and scores low, because the same measure taken over a larger
     * radius separates them - an inlet stays enclosed, a headland does not.
     */
    private fun harbourQuality(
      region: CellRegion,
      submerged: BooleanArray,
      elevation: Grid,
      toShore: Grid,
      seaLevel: Double,
      params: HabitabilityParams
    ): Grid {
      val metres = region.resolution.metresPerCell
      val near = max(1, (2_500.0 / metres).toInt())
      val far = max(near + 1, (9_000.0 / metres).toInt())

      return Grid.parallel(region.width, region.height) { x, y ->
        val i = y * region.width + x
        if (submerged[i]) {
          0.0
        } else {
          val access = exp(-toShore.data[i] / params.harbourRange)
          if (access < 0.02) {
            0.0
          } else {
            val enclosedNear = waterFraction(region, submerged, x, y, near)
            val enclosedFar = waterFraction(region, submerged, x, y, far)
            // Sheltered means enclosed close in but *not* out to sea further away. On a headland both are
            // high and the difference vanishes; at the head of a fjord the near value stays high while the
            // far one falls, because the land wraps round.
            val shelter = (enclosedNear - enclosedFar * 0.75).coerceIn(0.0, 1.0)
            val deep = deepWaterNearby(region, elevation, submerged, x, y, far, seaLevel)
            access * shelter * deep
          }
        }
      }
    }

    private fun waterFraction(
      region: CellRegion,
      submerged: BooleanArray,
      x: Int,
      y: Int,
      radius: Int
    ): Double {
      var water = 0
      var total = 0
      for (dy in -radius..radius) {
        for (dx in -radius..radius) {
          if (dx * dx + dy * dy > radius * radius) continue
          val nx = (x + dx).coerceIn(0, region.width - 1)
          val ny = (y + dy).coerceIn(0, region.height - 1)
          total++
          if (submerged[ny * region.width + nx]) water++
        }
      }
      return if (total == 0) 0.0 else water.toDouble() / total
    }

    /** How deep the nearby water gets, normalised. A harbour that dries out at low tide is not a harbour. */
    private fun deepWaterNearby(
      region: CellRegion,
      elevation: Grid,
      submerged: BooleanArray,
      x: Int,
      y: Int,
      radius: Int,
      seaLevel: Double
    ): Double {
      var deepest = 0.0
      for (dy in -radius..radius) {
        for (dx in -radius..radius) {
          val nx = (x + dx).coerceIn(0, region.width - 1)
          val ny = (y + dy).coerceIn(0, region.height - 1)
          val i = ny * region.width + nx
          if (!submerged[i]) continue
          deepest = max(deepest, seaLevel - elevation.data[i])
        }
      }
      return (deepest / 25.0).coerceIn(0.0, 1.0)
    }

    /**
     * Cost of moving one metre through a cell, relative to easy flat ground.
     *
     * Roads are routed over this, so what it encodes is what roads will end up doing: they follow valleys,
     * avoid forests, cross rivers as rarely as they can, and give up on open water entirely. Making river
     * crossings expensive rather than forbidden is what makes bridges appear at the *few* places worth
     * bridging instead of everywhere a route meets a stream.
     */
    private fun movementCost(
      region: CellRegion,
      elevation: Grid,
      discharge: Grid,
      waterLevel: Grid,
      biome: IntLayer,
      metres: Double,
      params: HabitabilityParams
    ): Grid = Grid.parallel(region.width, region.height) { x, y ->
      val i = y * region.width + x

      if (!waterLevel.data[i].isNaN()) {
        IMPASSABLE
      } else {
        val slope = elevation.gradient(x, y, metres)
        var cost = 1.0 + slope * SLOPE_PENALTY

        cost *= when (Biome.of(biome[region.minX + x, region.minY + y])) {
          Biome.TEMPERATE_RAINFOREST, Biome.TROPICAL_RAINFOREST -> 2.4
          Biome.TAIGA, Biome.TEMPERATE_FOREST, Biome.TROPICAL_SEASONAL_FOREST -> 1.7
          Biome.WETLAND -> 3.2
          Biome.BADLANDS, Biome.CLIFF -> 4.0
          Biome.DESERT -> 1.4
          else -> 1.0
        }

        if (discharge.data[i] >= params.waterDischarge) cost *= params.riverCrossingCost

        cost
      }
    }

    private fun cap(grid: Grid, region: CellRegion) {
      val limit = max(region.width, region.height) * region.resolution.metresPerCell
      for (i in grid.data.indices) {
        if (grid.data[i] > limit) grid.data[i] = limit
      }
    }

    /** Cost that routing treats as "do not go there". Finite so a path across a strait can still be found. */
    const val IMPASSABLE = 400.0

    private const val SLOPE_PENALTY = 14.0
  }
}

