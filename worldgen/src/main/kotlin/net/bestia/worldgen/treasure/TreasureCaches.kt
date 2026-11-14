package net.bestia.worldgen.treasure

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.dress.FixtureChannels
import net.bestia.worldgen.dress.FixtureKind
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureEvaluator
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.VectorFeature
import net.bestia.worldgen.voxel.PondWaterSampler
import net.bestia.worldgen.voxel.RiverWaterSampler
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Every treasure cache near a point: the ones [TreasureStage] stored, and the wild ones.
 *
 * The wild caches are a jittered lattice like `voxel/LootScatter`'s, a pure function of the seed and the land,
 * because at 16 per square kilometre a world holds a quarter of a million of them. A dug cache is the
 * server's to remember, keyed on [TreasureCache.id].
 */
class TreasureCaches(private val generated: GeneratedWorld) {

  private val config = generated.config
  private val params = generated.params.resolved.treasure
  private val wildSeed = GenRng.mix64(config.seed xor WILD_SALT)

  /** Caches whose spot lies within [radius] metres of ([x], [y]), horizontally. */
  fun near(x: Double, y: Double, radius: Double): List<TreasureCache> {
    val reach = radius + params.campReach + params.cliffProbe
    val land = Land(generated.world.features.query(Aabb(x - reach, y - reach, x + reach, y + reach)))

    val found = ArrayList<TreasureCache>()
    for (marker in land.storedCaches) {
      if (squared(marker.position.x - x, marker.position.y - y) > radius * radius) continue
      val floor = marker.attribute(TreasureChannels.FLOOR)
      found.add(
        TreasureCache(
          id = marker.id.value,
          x = marker.position.x,
          y = marker.position.y,
          floor = if (floor.isNaN()) land.groundAt(marker.position.x, marker.position.y) else floor,
          depth = marker.attribute(TreasureChannels.DEPTH),
          minTier = TreasureTier.entries[marker.attribute(TreasureChannels.MIN_TIER).toInt()],
          artifact = marker.attribute(TreasureChannels.ARTIFACT).toInt(),
          site = marker.attribute(TreasureChannels.SITE).toInt(),
          battlefieldAge = marker.attribute(TreasureChannels.BATTLEFIELD_AGE)
        )
      )
    }
    wildNear(x, y, radius, land, found)
    return found
  }

  private fun wildNear(x: Double, y: Double, radius: Double, land: Land, into: MutableList<TreasureCache>) {
    val cell = params.wildCellSize
    val ceiling = params.wildShare * params.hotspotBoost

    for (cellY in cellOf(y - radius)..cellOf(y + radius)) {
      for (cellX in cellOf(x - radius)..cellOf(x + radius)) {
        val key = GenRng.hash(wildSeed, cellX, cellY)
        val roll = GenRng.unit(key)
        if (roll >= ceiling) continue

        val spotX = (cellX + GenRng.unit(GenRng.mix64(key + 1))) * cell
        val spotY = (cellY + GenRng.unit(GenRng.mix64(key + 2))) * cell
        if (squared(spotX - x, spotY - y) > radius * radius) continue
        if (!isOnMap(spotX, spotY) || land.isWorked(spotX, spotY)) continue

        val ground = land.dryGroundAt(spotX, spotY) ?: continue
        val hotspot = land.isNearCamp(spotX, spotY) || land.isCliffFoot(spotX, spotY, ground)
        if (!hotspot && roll >= params.wildShare) continue

        into.add(
          TreasureCache(
            id = key,
            x = spotX,
            y = spotY,
            floor = ground,
            depth = params.depthAt(GenRng.unit(GenRng.mix64(key + 3))),
            minTier = TreasureTier.POOR,
            artifact = -1,
            site = -1
          )
        )
      }
    }
  }

  private fun cellOf(world: Double): Long {
    return floor(world / params.wildCellSize).toLong()
  }

  private fun isOnMap(x: Double, y: Double): Boolean {
    return x >= 0.0 && x < config.widthMetres && y >= 0.0 && y < config.heightMetres
  }

  private fun squared(dx: Double, dy: Double): Double {
    return dx * dx + dy * dy
  }

  /** What the features around one query say about the ground. Built per query, so it needs no locking. */
  private inner class Land(features: List<VectorFeature>) {

    val storedCaches = features.filter { it.kind == FeatureKind.TREASURE_CACHE }.filterIsInstance<PointMarker>()

    private val evaluator = FeatureEvaluator(features)
    private val rivers = RiverWaterSampler(features, config.voxelSize)
    private val ponds = PondWaterSampler(features)
    private val worked = features.filter { it.kind in WORKED }
    private val camps = features
      .filter { it.kind == FeatureKind.FIXTURE }
      .filterIsInstance<PointMarker>()
      .filter { it.attribute(FixtureChannels.KIND).toInt() == FixtureKind.CAMP.ordinal }

    fun groundAt(x: Double, y: Double): Double {
      return evaluator.heightAt(x, y, generated.base.heightAt(x, y))
    }

    /** The ground height, or null under water, ice and the sea. */
    fun dryGroundAt(x: Double, y: Double): Double? {
      val biome = generated.materializer.surface.biomeAt(x, y)
      if (biome == Biome.OCEAN || biome == Biome.LAKE || biome == Biome.ICE_SHEET) return null

      val ground = groundAt(x, y)
      val standing = generated.materializer.surface.waterLevelAt(x, y)
      val river = rivers.surfaceAt(x, y)
      val pond = ponds.surfaceAt(x, y)
      val water = maxOf(standing, if (river.isNaN()) standing else river, if (pond.isNaN()) standing else pond)
      return if (water > ground) null else ground
    }

    fun isWorked(x: Double, y: Double): Boolean {
      return worked.any { feature ->
        when (feature) {
          is AreaFeature -> feature.contains(x, y)
          is PointFeature -> squared(x - feature.center.x, y - feature.center.y) <= feature.radius * feature.radius
          else -> false
        }
      }
    }

    fun isNearCamp(x: Double, y: Double): Boolean {
      val reach = params.campReach * params.campReach
      return camps.any { squared(it.position.x - x, it.position.y - y) <= reach }
    }

    /** Ground nearby rises steeply above this spot, as it does under a cliff. */
    fun isCliffFoot(x: Double, y: Double, ground: Double): Boolean {
      for (k in 0 until CLIFF_PROBES) {
        val angle = 2.0 * PI * k / CLIFF_PROBES
        val probeX = x + params.cliffProbe * cos(angle)
        val probeY = y + params.cliffProbe * sin(angle)
        if (generated.base.heightAt(probeX, probeY) - ground >= params.cliffRise) return true
      }
      return false
    }
  }

  private companion object {
    val WORKED = setOf(FeatureKind.FIELD, FeatureKind.SETTLEMENT_GRADING, FeatureKind.CEMETERY, FeatureKind.BUILDING)
    const val WILD_SALT = 0x5452454153L
    const val CLIFF_PROBES = 8
  }
}
