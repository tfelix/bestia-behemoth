package net.bestia.worldgen.mana

import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementStage
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.fields.DistanceTransform
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.geo.GlacialStage
import net.bestia.worldgen.history.HistoryChannels
import net.bestia.worldgen.history.HistoryStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.PolylineFeature
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Tuning for [CorruptionStage]. */
data class CorruptionParams(

  /**
   * Share of the world's **land** that comes out corrupted.
   *
   * The designer knob this whole subsystem exists to expose, and the reason the threshold is solved rather
   * than configured: see [CorruptionStage] for why a fixed cutoff cannot hold a target across seeds.
   *
   * "Land" is dry ground clear of lakes, so the denominator does not move with the ocean share - which
   * legitimately runs from 0.05 to 0.85 and would otherwise make this mean seventeen different things.
   */
  val corruptedLandShare: Double = 0.10,

  /**
   * How far a settlement or road holds mana back, in metres. Raw, not detail-scaled.
   *
   * The radius a pre-industrial town actually works its hinterland over, and the same order as the
   * habitability catchments in `civ/Terms.kt`. Suppression is `exp(-d / this)`, so it is down to 37% at one
   * range and 5% at three: a town's clean ground is a region rather than a dot, and roads thread clean
   * corridors between towns - which is the safe-travel structure the game wants, for free.
   */
  val suppressionRange: Double = 12_000.0,

  /**
   * How much of the mana a settlement can quench at zero distance.
   *
   * Deliberately not 1.0. Even a capital leaves a trace, which keeps a corrupted cellar under a city
   * possible as a quest hook and keeps this out of the way of any later division by the field.
   */
  val suppressionStrength: Double = 0.85,

  /**
   * Multiplier on [SettlementTier.footprintRadius] for the disc that counts as zero distance.
   *
   * **This is the whole of the size weighting, and it is deliberately the only mechanism.** A boolean
   * distance transform cannot carry a per-source weight, and the alternative - stamping a falloff per
   * settlement, the `Terrain.valueField` idiom - cannot handle roads without a stamp per road cell. Sizing
   * the *core* instead turns the tier table that already exists into the weighting: 5.4 km for a city down
   * to 0.54 km for a hamlet.
   */
  val settlementCoreFactor: Double = 6.0,

  /**
   * Width of the clean-to-corrupt ramp, in mana units above the solved threshold.
   *
   * A step here would draw a contour line across the ground. At 0.04 against the default field the
   * transition is a two to four kilometre walk, and the gradient is what the chunk tier's blight dither
   * needs anyway.
   */
  val thresholdSoftness: Double = 0.04,

  /**
   * Corruption at or above which an ore body comes out as aetherite rather than as its metal.
   *
   * Above [CorruptionStage.CORRUPTED], so the fringe of a province still yields iron and aetherite means the
   * deep interior - which is what makes it worth the walk.
   */
  val aetheriteCorruption: Double = 0.6,

  /**
   * Metres over which a `WOUND` holds the field up on its own, whatever civilisation is nearby.
   *
   * ### Measured, after the first version shipped wounds standing on clean grass
   *
   * A wound is placed at the raw field's peak and kept eight kilometres clear of every settlement, which
   * sounded like enough and was not: **six of fifteen wounds over eight seeds came out with zero corruption
   * under them.** Suppression is what did it. `exp(-d / 12 km)` needs about twenty kilometres of clearance
   * before it stops mattering, and the distance is to the nearest settlement *or road* - one wound sat two
   * kilometres from a road threading past it. Widening the clearance cannot fix that without pushing wounds
   * off the mana peaks they are supposed to mark.
   *
   * So the wound lifts the field instead of hoping to survive it, and the causality is the right way round:
   * this is the place the mana came *from*, so it is the one place people cannot hold it back. That is also
   * what makes the corrupted land reliably reachable endgame territory rather than a thing that exists on
   * three worlds in five.
   *
   * At 2 500 m each wound guarantees a patch two to three kilometres across even in otherwise clean country -
   * a valley, not a dot - and three of them cover about 0.5% of a genesis world's land. The share stays exactly
   * on target regardless, because the quantile is solved *after* the lift is applied.
   */
  val woundRange: Double = 2_500.0
) : Params {

  init {
    require(corruptedLandShare in 0.0..1.0) {
      "corruptedLandShare must be a share, was $corruptedLandShare"
    }
    require(suppressionRange > 0.0) { "suppressionRange must be positive, was $suppressionRange" }
    require(suppressionStrength in 0.0..1.0) {
      "suppressionStrength must be a share, was $suppressionStrength"
    }
    require(settlementCoreFactor >= 0.0) {
      "settlementCoreFactor must not be negative, was $settlementCoreFactor"
    }
    require(thresholdSoftness > 0.0) { "thresholdSoftness must be positive, was $thresholdSoftness" }
    require(aetheriteCorruption in 0.0..1.0) {
      "aetheriteCorruption must be a share, was $aetheriteCorruption"
    }
    require(woundRange > 0.0) { "woundRange must be positive, was $woundRange" }
  }

  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    corruptedLandShare = source.double("corruptedLandShare", corruptedLandShare),
    suppressionRange = source.double("suppressionRange", suppressionRange),
    suppressionStrength = source.double("suppressionStrength", suppressionStrength),
    settlementCoreFactor = source.double("settlementCoreFactor", settlementCoreFactor),
    thresholdSoftness = source.double("thresholdSoftness", thresholdSoftness),
    aetheriteCorruption = source.double("aetheriteCorruption", aetheriteCorruption),
    woundRange = source.double("woundRange", woundRange)
  )

  override fun digest() = ParamsDigest()
    .put("corruptedLandShare", corruptedLandShare)
    .put("suppressionRange", suppressionRange)
    .put("suppressionStrength", suppressionStrength)
    .put("settlementCoreFactor", settlementCoreFactor)
    .put("thresholdSoftness", thresholdSoftness)
    .put("aetheriteCorruption", aetheriteCorruption)
    .put("woundRange", woundRange)
}

/**
 * What the mana did to the land, once civilisation had its say.
 *
 * ### Why this runs after history and [ManaStage] runs before it
 *
 * Suppression has to read the settlements that are **standing**, not the sites placement chose - a town
 * history razed does not hold anything back, and a ruin sitting in creeping corruption is one of the better
 * things this system produces for free. Standing-ness is only known after `HistoryStage`.
 *
 * But history also has to *react* to mana: blight is a thing that happens to a town. A single stage cannot
 * both precede and follow another, so the field is split at its natural seam - [ManaStage] is the geology and
 * this is the consequence. The causal order comes out right: a town suppresses mana **because it fought it**.
 *
 * ### The threshold is solved, not configured
 *
 * A fixed cutoff cannot hold "10% of the land" across seeds, for two compounding reasons. `MANA_DENSITY` is
 * already a rank over land, so a cutoff at `1 - share` would be exactly right *if nothing else happened* -
 * but suppression then pushes an unknown number of cells down, and how many depends on how many settlements
 * the seed produced and how they are spread. A world of few towns has far more unsuppressed land than a
 * crowded one.
 *
 * So the target is the parameter and the threshold is the output: take the `(1 - corruptedLandShare)`
 * quantile of the *suppressed* field over land, and ramp from there. `BiomeStage.rankConfidence` made the
 * same move for the same reason, and [LayerId.CORRUPTION] carries the same caveat it does - the value is
 * relative to a world.
 *
 * ### The distance transform is padded, because the world wraps and the transform does not
 *
 * `DistanceTransform` and `PointIndex` both work on a flat grid, and the genesis world wraps in **both**
 * axes. `geo/Plates.kt` gets away with ignoring that because the forced ocean margin makes the seam open
 * water and a plate boundary there is invisible. This does not get away with it: a town near one edge should
 * hold mana back near the other, and without the padding the land inside the margin reads as the remotest
 * place on the map.
 *
 * `TODO.md` already lists `WorldWrap` as having three callers while movement and pathing use naive
 * subtraction. This is not a fourth.
 */
class CorruptionStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: CorruptionParams = CorruptionParams()
) : Stage {

  override val id = ID
  override val version = 1
  override val paramsVersion get() = params.digest().value

  /**
   * Mana for the field, glacial and hydrology and biomes for the land mask, settlements for the roads and
   * the sites, history for which of those sites anybody still lives in.
   */
  override val dependencies = listOf(
    ManaStage.ID,
    GlacialStage.ID,
    HydrologyStage.ID,
    BiomeStage.ID,
    SettlementStage.ID,
    HistoryStage.ID
  )

  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Raster(LayerId.CORRUPTION),
    StageOutput.Raster(LayerId.CIVILISATION_DISTANCE)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val mana = Grid.from(ctx.layers.float(LayerId.MANA_DENSITY))
    val elevation = Grid.from(ctx.layers.float(LayerId.ELEVATION))
    val waterLevel = Grid.from(ctx.layers.float(LayerId.WATER_LEVEL))
    val biome = ctx.layers.int(LayerId.BIOME)
    val seaLevel = ctx.config.seaLevel

    val civDistance = civilisationDistance(ctx, region)

    // Suppressed field first, over every cell; the land mask decides only what the quantile is taken over.
    val suppressed = Grid(region.width, region.height)
    val woundLift = woundLift(ctx, region)
    for (i in suppressed.data.indices) {
      val quench = params.suppressionStrength * exp(-civDistance.data[i] / params.suppressionRange)
      // The lift is applied **after** the quench and as a floor rather than a term, which is the whole of what
      // it means: a wound is not extra mana for a town to fight, it is ground nobody can reclaim. See
      // `CorruptionParams.woundRange` for the measurement that put it here.
      val quenched = (mana.data[i] * (1.0 - quench)).coerceIn(0.0, 1.0)
      suppressed.data[i] = max(quenched, woundLift.data[i])
    }

    val landValues = ArrayList<Double>(suppressed.data.size / 2)
    for (i in suppressed.data.indices) {
      if (isStandableLand(elevation, waterLevel, biome, region, seaLevel, i)) {
        landValues.add(suppressed.data[i])
      }
    }

    val threshold = solveThreshold(landValues, params.corruptedLandShare)

    val corruption = Grid(region.width, region.height)
    for (i in corruption.data.indices) {
      // Zero over water rather than merely low. Corruption is a statement about ground somebody could stand
      // on, and a corrupted lake bed would be a place with no way to be there and no way to see it.
      if (!isStandableLand(elevation, waterLevel, biome, region, seaLevel, i)) continue

      // Centred on the threshold so the solved quantile lands at exactly CORRUPTED, which is what makes the
      // share this stage reports and the share the invariant measures the same number.
      corruption.data[i] = smoothstep(
        threshold - params.thresholdSoftness,
        threshold + params.thresholdSoftness,
        suppressed.data[i]
      )
    }

    return StageResult.of(
      corruption.toLayer(LayerId.CORRUPTION, region),
      civDistance.toLayer(LayerId.CIVILISATION_DISTANCE, region)
    )
  }

  /**
   * How much each cell's field is held up by a nearby wound, as a floor on the suppressed value.
   *
   * A stamped falloff per marker rather than a distance transform, and that is the right shape here for once:
   * there are at most three of them, so the `Terrain.valueField` idiom costs three discs, while the boolean
   * transform used for civilisation would give every wound the same weight *and* would need a second pass.
   *
   * The peak is 1.0 - the top of the field - so a wound's centre is inside the corrupted quantile by
   * construction whatever else the seed did. That is deliberate: `Invariants.checkWoundsAreInCorruptedGround`
   * then guards the *wiring* rather than the arithmetic, and the wiring is the part that can silently break
   * (this stage reading a feature kind that `HistoryStage` stopped emitting looks like nothing at all).
   *
   * No wrap handling, unlike [civilisationDistance]: a wound is a 2.5 km influence on a world at least 128 km
   * across, so a wound near one edge genuinely does nothing to the other. The seam trap only bites on ranges
   * comparable to the world.
   */
  private fun woundLift(ctx: GenContext, region: CellRegion): Grid {
    val lift = Grid(region.width, region.height)
    val metres = region.resolution.metresPerCell

    val wounds = ctx.features.query(region.toWorld())
      .filter { it.kind == FeatureKind.WOUND }
      .filterIsInstance<PointMarker>()
    if (wounds.isEmpty()) return lift

    for (wound in wounds) {
      // Three ranges out, `exp(-3)` is under 5% and far below the threshold, so the disc is where the work is.
      val reach = ceil(3.0 * params.woundRange / metres).toInt()
      val centreX = Math.floorDiv(wound.position.x.toInt(), metres.toInt()) - region.minX
      val centreY = Math.floorDiv(wound.position.y.toInt(), metres.toInt()) - region.minY

      for (dy in -reach..reach) {
        val y = centreY + dy
        if (y !in 0 until region.height) continue
        for (dx in -reach..reach) {
          val x = centreX + dx
          if (x !in 0 until region.width) continue

          val offsetX = (region.minX + x + 0.5) * metres - wound.position.x
          val offsetY = (region.minY + y + 0.5) * metres - wound.position.y
          val distance = kotlin.math.sqrt(offsetX * offsetX + offsetY * offsetY)

          val value = exp(-distance / params.woundRange)
          val at = y * region.width + x
          if (value > lift.data[at]) lift.data[at] = value
        }
      }
    }

    return lift
  }

  /**
   * The mana value at which the top [share] of land begins.
   *
   * Returns a value above 1 when there is no land at all, so nothing is corrupted rather than everything -
   * the safe direction for a degenerate world.
   */
  private fun solveThreshold(landValues: MutableList<Double>, share: Double): Double {
    if (landValues.isEmpty()) return Double.MAX_VALUE
    landValues.sort()
    val index = ((1.0 - share) * (landValues.size - 1)).toInt().coerceIn(0, landValues.size - 1)
    return landValues[index]
  }

  /**
   * Metres to the nearest standing settlement or road, wrap-padded and capped.
   *
   * The mask is rasterised from the vector tier rather than from any raster, because neither settlements nor
   * roads have one - and because a road's *centreline* is what suppression should measure from, not the cells
   * its bounding box covers.
   */
  private fun civilisationDistance(ctx: GenContext, region: CellRegion): Grid {
    val metres = region.resolution.metresPerCell
    val standing = standingSettlements(ctx, region)

    // Pad by three suppression ranges: beyond that `exp(-d/range)` is under 5% and a distance read from the
    // wrong side of the seam cannot change an answer anybody can see. Capped at the world's own extent,
    // because padding by more than the world would copy a copy.
    val padCells = ceil(3.0 * params.suppressionRange / metres).toInt()
    val padX = if (ctx.config.wrapX) min(padCells, region.width) else 0
    val padY = if (ctx.config.wrapY) min(padCells, region.height) else 0

    val paddedWidth = region.width + 2 * padX
    val paddedHeight = region.height + 2 * padY
    val mask = BooleanArray(paddedWidth * paddedHeight)

    fun mark(worldX: Double, worldY: Double) {
      // Into padded coordinates, then replicated into every pad copy that can see this cell. Marking the
      // source cell and letting the wrap-copy pass handle the rest would need a second full sweep; marking
      // all nine images directly is one line and cannot get out of step with itself.
      val cellX = Math.floorDiv(worldX.toInt(), metres.toInt()) - region.minX
      val cellY = Math.floorDiv(worldY.toInt(), metres.toInt()) - region.minY
      if (cellX !in 0 until region.width || cellY !in 0 until region.height) return

      for (imageY in -1..1) {
        for (imageX in -1..1) {
          val px = cellX + padX + imageX * region.width
          val py = cellY + padY + imageY * region.height
          if (px in 0 until paddedWidth && py in 0 until paddedHeight) {
            mask[py * paddedWidth + px] = true
          }
        }
      }
    }

    for (feature in ctx.features.query(region.toWorld())) {
      when (feature.kind) {
        FeatureKind.SETTLEMENT -> {
          val marker = feature as? PointMarker ?: continue
          val index = marker.attribute(SettlementChannels.INDEX).toInt()
          if (index !in standing) continue

          val tier = SettlementTier.entries[marker.attribute(SettlementChannels.TIER).toInt()]
          val radius = tier.footprintRadius * params.settlementCoreFactor
          val steps = ceil(radius / (metres * 0.5)).toInt()

          for (dy in -steps..steps) {
            for (dx in -steps..steps) {
              val offsetX = dx * metres * 0.5
              val offsetY = dy * metres * 0.5
              if (offsetX * offsetX + offsetY * offsetY > radius * radius) continue
              mark(marker.position.x + offsetX, marker.position.y + offsetY)
            }
          }
        }

        FeatureKind.ROAD -> {
          val road = feature as? PolylineFeature ?: continue
          // Half-cell steps so no cell the road crosses is missed - `ResourceStage.convergentDistance` walks
          // its faults the same way and for the same reason.
          var s = 0.0
          while (s <= road.centerline.length) {
            val point = road.centerline.pointAt(s)
            mark(point.x, point.y)
            s += metres * 0.5
          }
        }

        else -> Unit
      }
    }

    val padded = DistanceTransform.euclideanMetres(paddedWidth, paddedHeight, metres) { x, y ->
      mask[y * paddedWidth + x]
    }

    // A world with no standing settlement and no road is a legitimate seed - history can empty every town -
    // so cap rather than letting MAX_VALUE reach the exponential.
    val cap = max(region.width, region.height) * metres
    val out = Grid(region.width, region.height)
    for (y in 0 until region.height) {
      for (x in 0 until region.width) {
        val value = padded.data[(y + padY) * paddedWidth + (x + padX)]
        out.data[y * region.width + x] = min(value, cap)
      }
    }

    return out
  }

  /**
   * Indices of the settlements somebody still lives in.
   *
   * The `SETTLEMENT` / `SETTLEMENT_HISTORY` join on [SettlementChannels.INDEX], filtered the way `TownStage`
   * and `EconomyStage` filter it: a founding year of zero means history never founded the site, and a
   * non-zero abandonment year means it did and then it ended.
   */
  private fun standingSettlements(ctx: GenContext, region: CellRegion): Set<Int> {
    val standing = HashSet<Int>()
    for (feature in ctx.features.query(region.toWorld())) {
      if (feature.kind != FeatureKind.SETTLEMENT_HISTORY) continue
      val past = feature as? PointMarker ?: continue
      if (past.attribute(HistoryChannels.FOUNDED_YEAR).toInt() == 0) continue
      if (past.attribute(HistoryChannels.ABANDONED_YEAR).toInt() != 0) continue
      standing.add(past.attribute(HistoryChannels.INDEX).toInt())
    }
    return standing
  }

  companion object {
    val ID = StageId("corruption")

    /**
     * The one definition of "this ground is corrupted".
     *
     * Public and referenced rather than repeated, because four separate readers ask the question - the spawn
     * stage, the ore materialiser, the invariants and the viewer - and four copies of `0.5` is four chances
     * for one of them to drift. It is 0.5 rather than any other number because
     * [CorruptionParams.thresholdSoftness] centres the ramp on the solved quantile, so the share of land at
     * or above this value *is* [CorruptionParams.corruptedLandShare] by construction.
     */
    const val CORRUPTED = 0.5

    /** Hermite smoothstep. Zero derivative at both ends, so the ramp gains no crease at either edge. */
    private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
      val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
      return t * t * (3.0 - 2.0 * t)
    }
  }
}
