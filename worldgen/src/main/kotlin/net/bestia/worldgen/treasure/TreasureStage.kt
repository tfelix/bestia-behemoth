package net.bestia.worldgen.treasure

import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.history.HistoryStage
import net.bestia.worldgen.history.SiteChannels
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Buries a treasure cache at the history sites that would have one: cave hoards, tombs, ruins, battlefields and
 * outposts.
 *
 * Only these are stored. The far more numerous wild caches are a lattice nobody stores, see [TreasureCaches].
 */
class TreasureStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: TreasureParams = TreasureParams()
) : Stage {

  override val id = ID

  // 2: a monument holds a cache when an artifact is sealed in it.
  override val version = 2

  override val paramsVersion get() = params.digest().value

  override val dependencies = listOf(HistoryStage.ID)

  override val scale = StageScale.WORLD

  override val outputs = listOf(StageOutput.Vector(FeatureKind.TREASURE_CACHE))

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val sites = ctx.features.query(region.toWorld())
      .filter { it.kind in BURIED_AT }
      .filterIsInstance<PointMarker>()
      .sortedBy { it.attribute(SiteChannels.SITE) }

    val nextId = FeatureIds.allocator(id)
    val caches = ArrayList<VectorFeature>()
    for (site in sites) {
      val siteIndex = site.attribute(SiteChannels.SITE).toLong()
      val h = GenRng.hash(ctx.seed, id.hash, version.toLong(), siteIndex)
      val artifact = site.attribute(SiteChannels.ARTIFACT).toInt()
      if (artifact < 0 && GenRng.unit(h) >= chanceAt(site.kind)) continue

      caches.add(
        PointMarker(
          id = nextId(),
          kind = FeatureKind.TREASURE_CACHE,
          position = spotIn(site, h),
          attributes = StationTable.Builder(stationCount = 1)
            .channel(TreasureChannels.DEPTH) { params.depthAt(GenRng.unit(GenRng.mix64(h + 3))) }
            .channel(TreasureChannels.MIN_TIER) { minTierOf(site, artifact).ordinal.toDouble() }
            .channel(TreasureChannels.ARTIFACT) { artifact.toDouble() }
            .channel(TreasureChannels.SITE) { siteIndex.toDouble() }
            .channel(TreasureChannels.FLOOR) { site.attribute(SiteChannels.ELEVATION) }
            .build()
        )
      )
    }
    return StageResult(features = caches)
  }

  private fun chanceAt(kind: FeatureKind): Double {
    return when (kind) {
      FeatureKind.CAVE_HOARD -> 1.0
      FeatureKind.TOMB -> params.tombChance
      FeatureKind.RUIN, FeatureKind.ASH_RUIN -> params.ruinChance
      FeatureKind.BATTLEFIELD -> params.battlefieldChance
      // Only the artifact a people sealed into it.
      FeatureKind.MONUMENT -> 0.0
      else -> params.outpostChance
    }
  }

  /** A hoard lies where the cave holds it. Anything else lies somewhere inside the site, not at its heart. */
  private fun spotIn(site: PointMarker, h: Long): Vec2d {
    if (site.kind == FeatureKind.CAVE_HOARD) return site.position

    val reach = site.attribute(SiteChannels.RADIUS) * SITE_SPREAD * sqrt(GenRng.unit(GenRng.mix64(h + 1)))
    val angle = 2.0 * PI * GenRng.unit(GenRng.mix64(h + 2))
    return Vec2d(site.position.x + reach * cos(angle), site.position.y + reach * sin(angle))
  }

  /** What the history of the place is worth, whatever the danger of the country around it says. */
  private fun minTierOf(site: PointMarker, artifact: Int): TreasureTier {
    if (artifact >= 0) return TreasureTier.SPLENDID

    val hostTier = site.attribute(SiteChannels.TIER).toInt()
    val bigTown = hostTier in 0..SettlementTier.TOWN.ordinal
    return when (site.kind) {
      FeatureKind.CAVE_HOARD, FeatureKind.TOMB -> TreasureTier.RICH
      FeatureKind.RUIN, FeatureKind.ASH_RUIN -> if (bigTown) TreasureTier.RICH else TreasureTier.MODEST
      else -> TreasureTier.MODEST
    }
  }

  companion object {
    val ID = StageId("treasure")

    private val BURIED_AT = setOf(
      FeatureKind.CAVE_HOARD,
      FeatureKind.TOMB,
      FeatureKind.RUIN,
      FeatureKind.ASH_RUIN,
      FeatureKind.BATTLEFIELD,
      FeatureKind.FORT,
      FeatureKind.MONASTERY,
      FeatureKind.MONUMENT
    )

    /** Share of a site's radius a cache may lie out from its centre. */
    private const val SITE_SPREAD = 0.8
  }
}
