package net.bestia.worldgen.history

import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.civ.Culture
import net.bestia.worldgen.civ.HabitabilityStage
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementStage
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Order
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.core.SiteRecord
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.geo.TectonicsStage
import net.bestia.worldgen.geo.VolcanismStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.karst.CaveStage
import net.bestia.worldgen.mana.ManaStage
import net.bestia.worldgen.resource.ResourceStage
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.max

/**
 * Step 10: history.
 *
 * Reads the settlement sites placement produced, runs [HistorySim] over them for a thousand years, and
 * emits two things: the [Chronicle] - the world tier's third product, and the actual output of this stage -
 * and the physical residue of what happened, as vector features that chunk generation can see.
 *
 * ### Why this runs before town layout rather than after
 *
 * The architecture document numbers town layout 8 and history 10, and the *dependency* runs the other way:
 * a town's walls enclose the extent it had when it was threatened, its ruins are settlements history
 * destroyed, and how much of it is stone follows the wealth history gave it. Laying out a town first and
 * retrofitting history into it would mean either regenerating the layout or leaving the walls unexplained.
 *
 * So the pipeline order is `settlements -> history -> towns -> economy`, and the build order's numbering is
 * a statement about what to *implement* first rather than about what depends on what. Nothing else changes:
 * history still does not place settlements, which is the property that made retrofitting sound right.
 */
class HistoryStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: HistoryParams = HistoryParams()
) : Stage {

  override val id = ID

  override val version = 1

  override val paramsVersion get() = GenRng.hash(params.digest().value, Culture.catalogueDigest(), SettlementTier.catalogueDigest(), EventKind.catalogueDigest(), Names.catalogueDigest(), Order.catalogueDigest())
  override val dependencies = listOf(
    TectonicsStage.ID, ClimateStage.ID, ErosionStage.ID, HydrologyStage.ID, BiomeStage.ID,
    ResourceStage.ID, CaveStage.ID, HabitabilityStage.ID, SettlementStage.ID,
    // The *raw* mana field, and it can only be the raw one: `corruption` reads this stage's chronicle, so it
    // runs after it. See `mana/CorruptionStage`, where that split is argued from the other side - it is what
    // lets a town suffer the blight and then hold it back, and what lets a town history razed stop holding it
    // back at all.
    ManaStage.ID,
    // The volcanism field and, more to the point, the vent markers. Eruptions are rolled per vent now, so this
    // stage needs the volcanoes themselves rather than a proximity field it derived from the faults - see
    // `HistorySim.resolveEruptions`.
    VolcanismStage.ID
  )
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Vector(FeatureKind.SETTLEMENT_HISTORY),
    StageOutput.Vector(FeatureKind.RUIN),
    StageOutput.Vector(FeatureKind.ASH_RUIN),
    StageOutput.Vector(FeatureKind.BATTLEFIELD),
    StageOutput.Vector(FeatureKind.TOMB),
    StageOutput.Vector(FeatureKind.MONUMENT),
    StageOutput.Vector(FeatureKind.MINE),
    StageOutput.Vector(FeatureKind.MONASTERY),
    StageOutput.Vector(FeatureKind.FORT),
    StageOutput.Vector(FeatureKind.LIGHTHOUSE),
    StageOutput.Vector(FeatureKind.CAVE_HOARD),
    StageOutput.Vector(FeatureKind.WOUND),
    StageOutput.Vector(FeatureKind.SHRINE),
    StageOutput.History
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val facts = readSites(ctx, region)
    if (facts.isEmpty()) {
      // A world with nowhere to live has no history, and saying so is better than simulating an empty one.
      return StageResult(chronicle = Chronicle(1, params.years, emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), 0))
    }

    val streamBase = GenRng.hash(ctx.seed, id.hash, version.toLong())
    val candidates = SpecialSiteCandidates.read(ctx, region, facts, params)
    val chronicle = HistorySim(params, facts, readVents(ctx, region), candidates, streamBase, ctx.seed).run()

    return StageResult(features = emit(chronicle, facts), chronicle = chronicle)
  }

  // --- Reading the world -----------------------------------------------------------------------------

  /**
   * Every settlement site, with the handful of local facts the simulation needs.
   *
   * Sorted by [SettlementChannels.INDEX] and asserted to be dense from zero, because the whole join
   * between this stage, town layout and the economy is that index - and a gap in it would silently shift
   * every record after the gap onto the wrong town.
   */
  private fun readSites(ctx: GenContext, region: CellRegion): List<SiteFacts> {
    val markers = ctx.features.query(region.toWorld())
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
      .sortedBy { it.attribute(SettlementChannels.INDEX) }
    if (markers.isEmpty()) return emptyList()

    val fertility = ctx.layers.float(LayerId.SOIL_FERTILITY)
    val sediment = ctx.layers.float(LayerId.SEDIMENT)
    val discharge = ctx.layers.float(LayerId.DISCHARGE)
    val elevation = ctx.layers.float(LayerId.ELEVATION)
    val distanceToOcean = ctx.layers.float(LayerId.DISTANCE_TO_OCEAN)
    val resourceValue = ctx.layers.float(LayerId.RESOURCE_VALUE)

    // A plain point sample of the layer, not `manaField`'s neighbourhood maximum. That machinery exists because
    // `blightMana` is a *threshold* - a town on the very edge of a province had to read its neighbour's ground or
    // the gate never opened - and volcanism here is a linear multiplier on nothing at all now that eruptions are
    // rolled per vent. What it still feeds is `SiteFacts.volcanism`, which the economy and the site prose read.
    val volcanism = ctx.layers.float(LayerId.VOLCANISM)
    val mana = manaField(ctx, region, params)

    return markers.mapIndexed { ordinal, marker ->
      val declared = marker.attribute(SettlementChannels.INDEX).toInt()
      require(declared == ordinal) {
        "Settlement indices must be dense from zero; found $declared at position $ordinal"
      }

      val x = marker.position.x
      val y = marker.position.y
      val above = elevation.sampleBilinear(x, y) - ctx.config.seaLevel

      SiteFacts(
        index = ordinal,
        position = marker.position,
        tier = SettlementTier.entries[marker.attribute(SettlementChannels.TIER).toInt()],
        cultureIndex = marker.attribute(SettlementChannels.CULTURE).toInt(),
        potential = marker.attribute(SettlementChannels.POPULATION).toInt().coerceAtLeast(10),
        habitability = marker.attribute(SettlementChannels.HABITABILITY),
        fertility = fertility.sampleBilinear(x, y).coerceIn(0.0, 1.0),
        volcanism = volcanism.sampleBilinear(x, y).coerceIn(0.0, 1.0),
        // Deep recent sediment, a big river, and not much height above it. All three, because any one on
        // its own describes half the good farmland in the world.
        floodRisk = (
            (sediment.sampleBilinear(x, y) / 8.0).coerceIn(0.0, 1.0) *
                (discharge.sampleBilinear(x, y) / FLOOD_DISCHARGE).coerceIn(0.0, 1.0) *
                (1.0 - (above / FLOOD_RELIEF).coerceIn(0.0, 1.0))
            ),
        coastal = distanceToOcean.sampleBilinear(x, y) < COASTAL_RANGE,
        resourceValue = resourceValue.sampleBilinear(x, y).coerceIn(0.0, 1.0),
        mana = mana(marker.position)
      )
    }
  }

  /**
   * The volcanoes, as history sees them.
   *
   * Sorted by the dense index `VolcanismStage` assigned, and asserted to *be* dense, for the reason
   * `readSites` asserts the same of settlements: the per-vent eruption roll keys on this index, so a gap wastes a
   * stream and a duplicate makes two volcanoes erupt in lockstep for the life of the world. A world with no
   * convergent boundary and no hotspot on land legitimately has none, and then nothing erupts.
   *
   * This replaced a `volcanismField` that lived here and computed a proximity field from the convergent `FAULT`
   * polylines. Worth recording what that cost: the reach it tapered over was a bare `private const val` reaching
   * **no version number at all**, so retuning how far an arc's influence spread would have moved neither
   * `paramsVersion` nor `pipelineVersion`. Moving the model into `VolcanismStage` put it behind `VolcanismParams`.
   */
  private fun readVents(ctx: GenContext, region: CellRegion): List<VentFacts> {
    val markers = ctx.features.query(region.toWorld())
      .filter { it.kind == FeatureKind.VOLCANIC_VENT }
      .filterIsInstance<PointMarker>()
      .sortedBy { it.attribute(VolcanismStage.CHANNEL_INDEX) }

    return markers.mapIndexed { ordinal, marker ->
      val declared = marker.attribute(VolcanismStage.CHANNEL_INDEX).toInt()
      require(declared == ordinal) {
        "Vent indices must be dense from zero; found $declared at position $ordinal"
      }

      VentFacts(
        index = ordinal,
        position = marker.position,
        strength = marker.attribute(VolcanismStage.CHANNEL_STRENGTH).coerceIn(0.0, 1.0)
      )
    }
  }

  /**
   * How exposed a place is to the mana, as a function of world position.
   *
   * ### A proximity maximum rather than a point sample, and what that is actually worth
   *
   * The worst mana anywhere within [HistoryParams.blightRange], tapered linearly by distance. At zero distance it
   * degenerates to the point sample, so a town standing inside a province still reads its own ground.
   *
   * This used to say "the same construction `volcanismField` uses, so the two gates on a disaster are the same
   * shape". They are no longer the same shape and the difference is deliberate: volcanism is now a plain point
   * sample of `LayerId.VOLCANISM`, because a blight is a *threshold* that a town on a province's edge would
   * otherwise never cross, while an eruption is rolled per vent and needs no field at all.
   *
   * Two reasons, and neither is "otherwise nothing fires" - **that claim was written here first and the
   * measurement refuted it.** A point sample puts 62 of 224 settlement sites over the blight threshold against
   * the proximity field's 79, so the blight would have worked either way. (The claim is true of the *corruption*
   * field, which is suppressed to nearly zero at every settlement by construction; it is not true of the raw
   * geological field, and this stage reads the raw one. Carrying an argument across that split is what went
   * wrong.)
   *
   * What it is really worth:
   *
   * - **A town is not a cell.** `MANA_DENSITY` is a kilometre raster and a settlement's worked hinterland spans
   *   several cells of it, so "is the mana high in the cell the market square happens to fall in" is an
   *   arbitrary question at this resolution. Blight reaching a town's *fields* is a statement about the
   *   neighbourhood, and this is that statement.
   * - **Exposure is continuous in position.** Under a point sample a town two hundred metres outside a province
   *   edge reads nothing and one two hundred metres inside reads everything. The taper removes the step, which
   *   matters because [HistoryParams.blightMana] is a threshold and a threshold on a discontinuous field turns
   *   a rounding difference into a different history.
   *
   * The range is **raw, not detail-scaled**, because a mana province is 8-13 km across on every world size -
   * `ManaStage`'s own KDoc argues that at length. A `scaleByLength` here would make the blight a different
   * mechanic on the demo world than on genesis.
   */
  private fun manaField(ctx: GenContext, region: CellRegion, params: HistoryParams): (Vec2d) -> Double {
    if (!ctx.layers.contains(LayerId.MANA_DENSITY)) return { 0.0 }
    val mana = ctx.layers.float(LayerId.MANA_DENSITY)

    val metres = region.resolution.metresPerCell
    val range = params.blightRange
    val reach = Math.ceil(range / metres).toInt()

    return { position ->
      val centreX = (position.x / metres).toInt()
      val centreY = (position.y / metres).toInt()
      var worst = 0.0

      for (dy in -reach..reach) {
        for (dx in -reach..reach) {
          val distance = Math.sqrt((dx * dx + dy * dy).toDouble()) * metres
          if (distance > range) continue
          val density = mana[centreX + dx, centreY + dy].toDouble()
          if (density.isNaN()) continue
          worst = max(worst, density * (1.0 - distance / range))
        }
      }

      worst.coerceIn(0.0, 1.0)
    }
  }

  // --- Emitting the residue --------------------------------------------------------------------------

  /**
   * The chronicle turned into features: one history marker per settlement, one marker per site.
   *
   * A marker per settlement including the ones that were never founded, because "nobody ever settled here"
   * is a fact the town stage needs in order not to lay out a town, and its absence would be
   * indistinguishable from a missing dependency.
   */
  private fun emit(chronicle: Chronicle, facts: List<SiteFacts>): List<VectorFeature> {
    val nextId = FeatureIds.allocator(id)
    val out = ArrayList<VectorFeature>(facts.size + chronicle.sites.size)

    for (record in chronicle.settlements) {
      val site = facts[record.index]
      val civ = record.ownerCiv.takeIf { it >= 0 }?.let { chronicle.civs[it] }

      out.add(
        PointMarker(
          id = nextId(),
          kind = FeatureKind.SETTLEMENT_HISTORY,
          position = site.position,
          attributes = StationTable.Builder(1)
            .channel(HistoryChannels.INDEX) { record.index.toDouble() }
            .channel(HistoryChannels.FOUNDED_YEAR) { record.foundedYear.toDouble() }
            .channel(HistoryChannels.ABANDONED_YEAR) { record.abandonedYear.toDouble() }
            .channel(HistoryChannels.POPULATION) { record.population.toDouble() }
            .channel(HistoryChannels.WEALTH) { record.wealth }
            .channel(HistoryChannels.OWNER_CIV) { record.ownerCiv.toDouble() }
            .channel(HistoryChannels.CULTURE) { site.cultureIndex.toDouble() }
            .channel(HistoryChannels.TIMES_SACKED) { record.timesSacked.toDouble() }
            .channel(HistoryChannels.WALL_YEAR) { record.wallYear.toDouble() }
            .channel(HistoryChannels.WALL_POPULATION) { record.wallPopulation.toDouble() }
            .channel(HistoryChannels.NAME_SEED) { record.nameSeed.toDouble() }
            .channel(HistoryChannels.OLD_NAME_SEED) { record.oldNameSeed.toDouble() }
            .channel(HistoryChannels.TECHNOLOGY) { civ?.technology ?: 0.0 }
            .channel(HistoryChannels.ORDER) { (civ?.sworn?.ordinal ?: -1).toDouble() }
            .build()
        )
      )
    }

    for (record in chronicle.sites) {
      out.add(siteMarker(nextId(), record, facts))
    }

    return out
  }

  private fun siteMarker(featureId: FeatureId, record: SiteRecord, facts: List<SiteFacts>): PointMarker {
    val host = facts.getOrNull(record.settlement)
    val tier = host?.tier?.ordinal ?: -1

    return PointMarker(
      id = featureId,
      kind = when (record.kind) {
        SiteKind.RUIN -> FeatureKind.RUIN
        SiteKind.ASH_RUIN -> FeatureKind.ASH_RUIN
        SiteKind.BATTLEFIELD -> FeatureKind.BATTLEFIELD
        SiteKind.TOMB -> FeatureKind.TOMB
        SiteKind.MONUMENT -> FeatureKind.MONUMENT
        SiteKind.MINE -> FeatureKind.MINE
        SiteKind.MONASTERY -> FeatureKind.MONASTERY
        SiteKind.FORT -> FeatureKind.FORT
        SiteKind.LIGHTHOUSE -> FeatureKind.LIGHTHOUSE
        SiteKind.HOARD -> FeatureKind.CAVE_HOARD
        SiteKind.WOUND -> FeatureKind.WOUND
        SiteKind.SHRINE -> FeatureKind.SHRINE
      },
      position = record.position,
      attributes = StationTable.Builder(1)
        .channel(SiteChannels.SITE) { record.index.toDouble() }
        .channel(SiteChannels.YEAR) { record.year.toDouble() }
        .channel(SiteChannels.RADIUS) { record.radius }
        .channel(SiteChannels.DECAY) { record.decay }
        .channel(SiteChannels.SETTLEMENT) { record.settlement.toDouble() }
        .channel(SiteChannels.CULTURE) { (host?.cultureIndex ?: 0).toDouble() }
        .channel(SiteChannels.TIER) { tier.toDouble() }
        .channel(SiteChannels.NAME_SEED) { record.nameSeed.toDouble() }
        .channel(SiteChannels.ARTIFACT) { record.artifact.toDouble() }
        .channel(SiteChannels.FIGURE) { record.figure.toDouble() }
        .channel(SiteChannels.RESOURCE) { record.resource.toDouble() }
        // NaN for everything on the surface, where the ground's own height is the answer. Only a hoard, which
        // is in a cave, has a third coordinate of its own - and whatever spawns the treasure needs all three.
        .channel(SiteChannels.ELEVATION) { record.elevation }
        .channel(SiteChannels.ORDER) { (record.order?.ordinal ?: -1).toDouble() }
        .build()
    )
  }

  companion object {
    val ID = StageId("history")

    /** Metres from a convergent boundary within which a settlement is at risk of eruption. */

    /** Discharge at which a river is big enough to take out the lower town, in m3/s. */
    private const val FLOOD_DISCHARGE = 20.0

    /** Metres above the water at which a settlement is out of the flood's reach. */
    private const val FLOOD_RELIEF = 25.0

    private const val COASTAL_RANGE = 6_000.0
  }
}

/** Station channels on a [FeatureKind.SETTLEMENT_HISTORY] marker. */
object HistoryChannels {

  /** Joins to [SettlementChannels.INDEX]. */
  const val INDEX = "index"

  const val FOUNDED_YEAR = "founded_year"

  /** 0 while it stands. */
  const val ABANDONED_YEAR = "abandoned_year"

  /** Present-day population, as history left it - not the potential placement decided. */
  const val POPULATION = "population"

  const val WEALTH = "wealth"

  /** Index into [Chronicle.civs], or -1 for a ruin. */
  const val OWNER_CIV = "owner_civ"

  const val CULTURE = "culture"
  const val TIMES_SACKED = "times_sacked"

  /** 0 for an unwalled place. */
  const val WALL_YEAR = "wall_year"

  /** Population when the walls went up, which is the extent they enclose. */
  const val WALL_POPULATION = "wall_population"

  /**
   * Seed for [Names.place]. Masked to 48 bits so a `Double` holds it exactly - see [Names].
   */
  const val NAME_SEED = "name_seed"

  /** The name before the last conquest, or 0. */
  const val OLD_NAME_SEED = "old_name_seed"

  /** Owning civ's technology, 0 to 1. Copied here so a town does not have to reach into the chronicle. */
  const val TECHNOLOGY = "technology"

  /**
   * [net.bestia.worldgen.core.Order] ordinal of the owning civ, or -1 for an unaligned one - and for **every**
   * settlement on a world where the Orders play no part, which is most of them.
   *
   * Copied off the civ for [TECHNOLOGY]'s reason: a runtime asking "what do the people here believe" should not
   * have to walk from a settlement to its owner to its record. This is the channel a temple, an NPC's dialogue
   * or a lore query reads, and it is why the Orders reach the game without the chronicle being persisted.
   */
  const val ORDER = "order"
}

/** Station channels shared by [FeatureKind.RUIN], `BATTLEFIELD`, `TOMB` and `MONUMENT` markers. */
object SiteChannels {

  /** Index into [Chronicle.sites]. */
  const val SITE = "site"

  const val YEAR = "year"
  const val RADIUS = "radius"

  /** 0 = fresh, 1 = nothing but earthworks. What the materialiser thins the rubble by. */
  const val DECAY = "decay"

  /** Settlement it belongs to or replaced, or -1. */
  const val SETTLEMENT = "settlement"

  const val CULTURE = "culture"

  /** [SettlementTier] ordinal of the settlement this replaced, or -1. Decides how big a ruin field is. */
  const val TIER = "tier"

  const val NAME_SEED = "name_seed"

  /** Index into [Chronicle.artifacts], or -1. What is buried here. */
  const val ARTIFACT = "artifact"

  /**
   * Elevation in metres for a site that is not on the ground, NaN for one that is.
   *
   * Only [net.bestia.worldgen.core.SiteKind.HOARD] uses it. On every marker rather than only on hoards because
   * a channel that exists on some rows of a kind and not others is a reader's trap: `attribute` throws, and the
   * caller that wrapped it in `runCatching` to cope would then swallow a genuinely missing channel too.
   */
  const val ELEVATION = "elevation"

  /** Index into [Chronicle.figures], or -1. Who is buried here. */
  const val FIGURE = "figure"

  /**
   * [net.bestia.worldgen.resource.ResourceType] ordinal for a [SiteKind.MINE], -1 for every other kind.
   *
   * There is deliberately no `KIND` channel beside it: a site's kind is its [FeatureKind], so four kinds cost
   * nothing extra while one kind plus a type channel would cost a channel on every site marker in the world.
   * This one is here because a mine's *product* is not derivable from its kind.
   */
  const val RESOURCE = "resource"

  /**
   * [net.bestia.worldgen.core.Order] ordinal for a [SiteKind.SHRINE], -1 for every other kind.
   *
   * The one channel that *is* the "kind plus a type" the note above argues against, and [SiteKind.SHRINE]'s
   * own KDoc is where that trade is made. Worth reading the two together: the argument there was never that a
   * discriminator channel is wrong, it was that four kinds were free and a channel is not. This channel is not
   * free either - it is on every site marker in the world, like [RESOURCE] and [ELEVATION] - and it is paid for
   * because three structures, three name forms and three renderings all hang off it.
   *
   * On every marker rather than only on shrines for the reason [ELEVATION] records: a channel that exists on
   * some rows of a kind and not others makes `attribute` throw, and the caller who wraps it in `runCatching`
   * to cope then swallows a genuinely missing channel too.
   */
  const val ORDER = "order"
}
