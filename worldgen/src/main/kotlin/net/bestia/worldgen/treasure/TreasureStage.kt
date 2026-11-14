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
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Buries a treasure cache at the history sites that would have one: cave hoards, tombs, ruins and outposts. A
 * battlefield holds several, by its dead.
 *
 * Only these are stored. The far more numerous wild caches are a lattice nobody stores, see [TreasureCaches].
 */
class TreasureStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: TreasureParams = TreasureParams()
) : Stage {

  override val id = ID

  // 2: a monument holds a cache when an artifact is sealed in it.
  // 3: a battlefield holds shallow caches by its dead.
  override val version = 3

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
      val battlefield = site.kind == FeatureKind.BATTLEFIELD
      val count = when {
        battlefield -> cachesOnBattlefield(site)
        artifact >= 0 || GenRng.unit(h) < chanceAt(site.kind) -> 1
        else -> 0
      }

      for (k in 0 until count) {
        val hk = if (k == 0) h else GenRng.hash(h, k.toLong())
        // A lost artifact lies in one of a battlefield's caches, not in all of them.
        val held = if (k == 0) artifact else -1
        val depth = GenRng.unit(GenRng.mix64(hk + 3))
        caches.add(
          PointMarker(
            id = nextId(),
            kind = FeatureKind.TREASURE_CACHE,
            position = spotIn(site, hk),
            attributes = StationTable.Builder(stationCount = 1)
              .channel(TreasureChannels.DEPTH) { if (battlefield) params.battlefieldDepthAt(depth) else params.depthAt(depth) }
              .channel(TreasureChannels.MIN_TIER) { minTierOf(site, held).ordinal.toDouble() }
              .channel(TreasureChannels.ARTIFACT) { held.toDouble() }
              .channel(TreasureChannels.SITE) { siteIndex.toDouble() }
              .channel(TreasureChannels.FLOOR) { site.attribute(SiteChannels.ELEVATION) }
              .channel(TreasureChannels.BATTLEFIELD_AGE) { if (battlefield) ageOf(site) else Double.NaN }
              .build()
          )
        )
      }
    }
    return StageResult(features = caches)
  }

  /** More where more fell, fewer the older the field is, and always one: somebody dropped something. */
  private fun cachesOnBattlefield(site: PointMarker): Int {
    val left = site.attribute(SiteChannels.FALLEN) * params.battlefieldCachesPerDead * (1.0 - AGE_THINNING * ageOf(site))
    return left.roundToInt().coerceIn(1, params.battlefieldMaxCaches)
  }

  private fun ageOf(site: PointMarker): Double {
    return site.attribute(SiteChannels.DECAY).coerceIn(0.0, 1.0)
  }

  private fun chanceAt(kind: FeatureKind): Double {
    return when (kind) {
      FeatureKind.CAVE_HOARD -> 1.0
      FeatureKind.TOMB -> params.tombChance
      FeatureKind.RUIN, FeatureKind.ASH_RUIN -> params.ruinChance
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

    /** Share of a battlefield's caches that scavengers have dug up by the time the field is fully decayed. */
    private const val AGE_THINNING = 0.75
  }
}
