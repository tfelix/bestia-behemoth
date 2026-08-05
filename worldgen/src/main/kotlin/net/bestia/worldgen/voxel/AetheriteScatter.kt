package net.bestia.worldgen.voxel

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.resource.DepositChannels
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Quantize
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.sqrt

/** Tuning for [AetheriteScatter]. */
data class AetheriteParams(

  /**
   * Edge of the lattice square that holds at most one shard, in metres.
   *
   * Distinct from `CrystalParams.cellSize` (11 m) and `TownStructures.WOUND_SPIRE_SPACING` (7 m) so that the
   * three scatters interleave rather than landing on the same points. Unlike those two the distinctness is not
   * a *correctness* requirement here - [PropId] packs the kind alongside the cell, so two kinds on one spacing
   * still get different names - but a shared lattice would put a shard and a crystal at the same jittered
   * position whenever both fired, and one object inside another is the artefact the jitter exists to avoid.
   *
   * **Not a thing to change after a world ships**: a cell index is the durable half of a prop's identity, so
   * moving this renames every shard in the world. See [PropId].
   */
  val cellSize: Double = 8.0,

  /** How far a shard may wander from its cell's centre, as a share of [cellSize]. `VegetationParams`' trade. */
  val jitterShare: Double = 0.7,

  /**
   * How far the surface expression reaches, as a multiple of the orebody's own radius.
   *
   * Above 1 on purpose. An orebody is a flattened ellipsoid 21 to 70 m across sitting some metres down, and
   * what reaches daylight is not the body's outline but the weathered halo over it - so a field the size of the
   * body would be a suspiciously neat disc, and a field slightly wider reads as ground that has been pushed
   * around. At 2.0 the measured bodies give fields 50 to 140 m across, which is a place rather than a spot.
   */
  val reachOfRadius: Double = 2.0,

  /**
   * Metres below the surface past which a body has no surface expression at all.
   *
   * The gate that decides whether this feature exists on a given world, so it is worth stating what it was set
   * from. Surveyed over six worlds (128 and 256 cells, three seeds each), the corrupted bodies numbered 3 to 13
   * and their depths ran from 0 to 144 m; at 20 m, **one to nine of them per world** are shallow enough to show,
   * typically two to four. That is the rarity a prospecting hint wants - a handful of sites per world - and it
   * is above zero on every world measured, which is the property that matters most. A deposit at 141 m is under
   * a hundred metres of rock and has no business marking the grass.
   */
  val maxDepth: Double = 20.0,

  /**
   * Chance a lattice cell over the centre of a corrupted body holds a shard.
   *
   * Falls off to zero at the edge of [reachOfRadius], so this is the peak rather than the average. Well above
   * `CrystalParams.corruptedDensity` because the whole field is only a hundred metres across and a sparse
   * scatter over it would read as nothing at all - a player has to be able to tell they have found something.
   */
  val centreDensity: Double = 0.30,

  /** Share of shards that are the large variant at the centre of a rich body. */
  val largeShare: Double = 0.18,

  /** Shortest a shard stands, in metres. */
  val minHeight: Double = 0.6,

  /** Tallest a shard stands, in metres. Lower than a mana crystal: this is ore, not a growth. */
  val maxHeight: Double = 2.2
) : Params {

  init {
    require(cellSize > 0.0) { "cellSize must be positive, was $cellSize" }
    require(jitterShare in 0.0..1.0) { "jitterShare must be in [0,1], was $jitterShare" }
    require(reachOfRadius >= 1.0) { "reachOfRadius must be at least 1, was $reachOfRadius" }
    require(maxDepth >= 0.0) { "maxDepth must not be negative, was $maxDepth" }
    require(centreDensity in 0.0..1.0) { "centreDensity must be in [0,1], was $centreDensity" }
    require(largeShare in 0.0..1.0) { "largeShare must be in [0,1], was $largeShare" }
    require(minHeight > 0.0) { "minHeight must be positive, was $minHeight" }
    require(minHeight <= maxHeight) { "minHeight $minHeight exceeds maxHeight $maxHeight" }
  }

  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    cellSize = source.double("cellSize", cellSize),
    jitterShare = source.double("jitterShare", jitterShare),
    reachOfRadius = source.double("reachOfRadius", reachOfRadius),
    maxDepth = source.double("maxDepth", maxDepth),
    centreDensity = source.double("centreDensity", centreDensity),
    largeShare = source.double("largeShare", largeShare),
    minHeight = source.double("minHeight", minHeight),
    maxHeight = source.double("maxHeight", maxHeight)
  )

  override fun digest() = ParamsDigest()
    .put("cellSize", cellSize)
    .put("jitterShare", jitterShare)
    .put("reachOfRadius", reachOfRadius)
    .put("maxDepth", maxDepth)
    .put("centreDensity", centreDensity)
    .put("largeShare", largeShare)
    .put("minHeight", minHeight)
    .put("maxHeight", maxHeight)
}

/**
 * Aetherite shards on the ground over a shallow corrupted orebody.
 *
 * ### Why this is keyed on deposits and not on the corruption field
 *
 * `CrystalScatter` needs no features: mana crystals grow wherever the ambient mana is high, so a lattice hash
 * against one raster answers the whole question. These are ore, and the claim they make is specific - *dig here
 * and you get aetherite* - so they have to stand where a body that yields aetherite actually is. That means
 * reading `FeatureKind.ORE_DEPOSIT` and applying **the same corruption threshold `OreVeins` applies to the same
 * marker**, which is what makes the surface sign and the seam beneath it agree by construction rather than by
 * two tunings that happen to match.
 *
 * The first attempt at this gated on corrupted ground *and* thin soil, on the reasoning that corrupted rock
 * outcrops where nothing covers it. Surveying six worlds killed it: the conjunction left **zero to one site per
 * world, usually under an ice sheet**, which is the dead-code-path failure this module has shipped three times.
 * Corrupted bodies alone are 3 to 13 per world and shallow ones 1 to 9, so the orebody is the load-bearing gate
 * and the soil never was - a desert carries over a metre of sand anyway, which is the measurement that ended the
 * idea.
 *
 * ### Structured like `TownStructures.spireProps`, not like `CrystalScatter`
 *
 * Both are lattice scatters, but this one is built per chunk from the features the materialiser already
 * queried, because its inputs are markers rather than layers. The consequences are the spire pass's: a cell
 * inside two bodies' fields would yield two shards with one name, so [claimed] guards it the same way.
 */
class AetheriteScatter(
  private val config: WorldConfig,
  private val surface: SurfaceSampler,
  features: List<VectorFeature>,
  seed: Long,
  private val params: AetheriteParams = AetheriteParams(),
  corruption: FloatLayer? = null,
  aetheriteCorruption: Double = 1.0
) {

  private val cellUnits = Quantize.toFixed(params.cellSize)
  private val voxelUnits = Quantize.toFixed(config.voxelSize)
  private val scatterSeed = GenRng.mix64(seed xor SCATTER_SALT)

  /** One corrupted, shallow orebody and the field it puts on the surface. */
  private class Outcrop(
    val idSalt: Long,
    val x: Double,
    val y: Double,
    val reach: Double,
    val richness: Double
  )

  private val outcrops: List<Outcrop> = features
    .asSequence()
    .filter { it.kind == FeatureKind.ORE_DEPOSIT }
    .filterIsInstance<PointMarker>()
    .mapNotNull { marker ->
      runCatching {
        val severity = corruption?.sampleBilinear(marker.position.x, marker.position.y) ?: 0.0
        if (severity < aetheriteCorruption) return@runCatching null
        if (marker.attribute(DepositChannels.DEPTH) > params.maxDepth) return@runCatching null

        Outcrop(
          idSalt = marker.id.value,
          x = marker.position.x,
          y = marker.position.y,
          reach = marker.attribute(DepositChannels.RADIUS) * params.reachOfRadius,
          richness = marker.attribute(DepositChannels.RICHNESS)
        )
      }.getOrNull()
    }
    .filterNotNull()
    .toList()

  val isEmpty get() = outcrops.isEmpty()

  /**
   * The shards whose own position falls inside one chunk, as props.
   *
   * Ownership is the shard's voxel column in integers, for `VegetationScatter.propsIn`'s reason: a bounds test
   * on a closed interval hands a shard exactly on a chunk boundary to both of the chunks that share it.
   */
  fun propsIn(chunk: ChunkPos, site: PropSite, into: PropInstances) {
    if (outcrops.isEmpty()) return

    val bounds = config.chunkBounds(chunk)
    val fromX = Math.floorDiv(Quantize.toFixed(bounds.minX), cellUnits)
    val untilX = Math.floorDiv(Quantize.toFixed(bounds.maxX), cellUnits) + 1
    val fromY = Math.floorDiv(Quantize.toFixed(bounds.minY), cellUnits)
    val untilY = Math.floorDiv(Quantize.toFixed(bounds.maxY), cellUnits) + 1

    val chunkSize = config.chunkSize.toLong()
    val firstOfThisChunk = into.count

    for (outcrop in outcrops) {
      for (cellY in fromY until untilY) {
        for (cellX in fromX until untilX) {
          val shard = shardAt(outcrop, cellX, cellY) ?: continue

          if (Math.floorDiv(columnOf(shard.x), chunkSize).toInt() != chunk.x) continue
          if (Math.floorDiv(columnOf(shard.y), chunkSize).toInt() != chunk.y) continue

          val identity = PropId.of(PropKind.AETHERITE_SHARD, cellX, cellY)
          // Only reachable where two outcrops' fields overlap, which the ore dispersal pass makes unlikely -
          // `ResourceParams.oreSeparation` is 12 km against a field a hundred metres across. A guard rather
          // than a mechanism, and it costs a scan of a handful of props only on a chunk a second body reaches.
          if (outcrops.size > 1 && claimed(into, firstOfThisChunk, identity)) continue

          val ground = site.groundAt(shard.x, shard.y)
          if (ground.isNaN()) continue
          if (!standsOn(shard.x, shard.y, ground)) continue

          into.add(
            kind = PropKind.AETHERITE_SHARD,
            identity = identity,
            x = shard.x,
            y = shard.y,
            ground = ground,
            heightM = shard.height,
            // Always blighted: the ground is corrupted by definition here - it is what made the body yield
            // aetherite in the first place - so this is a statement about the shard rather than a sample of a
            // field that might disagree with itself at the fringe.
            flags = PropFlags.BLIGHTED or (if (shard.large) PropFlags.LARGE else 0)
          )
        }
      }
    }
  }

  /**
   * The shard one lattice cell holds over one outcrop, or null, before the ground height is known.
   *
   * A pure function of the cell index and the body's feature id, which is what lets two chunks reach the same
   * verdict about the same shard with no communication. Salted by the body so that two outcrops do not put
   * their shards on the same relative pattern.
   */
  private fun shardAt(outcrop: Outcrop, cellX: Long, cellY: Long): Shard? {
    val originX = cellX * cellUnits / Quantize.PER_METRE
    val originY = cellY * cellUnits / Quantize.PER_METRE

    val hash = GenRng.hash(GenRng.hash(scatterSeed, outcrop.idSalt), cellX, cellY)
    val shardX = originX + (0.5 + jitter(hash, JITTER_X_SALT)) * params.cellSize
    val shardY = originY + (0.5 + jitter(hash, JITTER_Y_SALT)) * params.cellSize

    val dx = shardX - outcrop.x
    val dy = shardY - outcrop.y
    val distance = sqrt(dx * dx + dy * dy)
    if (distance > outcrop.reach) return null

    // Linear falloff from the centre, so the field thins at its edge instead of ending on a rim. The same
    // shape `TownStructures.spireAt` uses inside a wound's rampart, and for the same reason.
    val inward = 1.0 - distance / outcrop.reach
    if (GenRng.unit(GenRng.mix64(hash xor PRESENCE_SALT)) >= params.centreDensity * inward) return null

    val large = GenRng.unit(GenRng.mix64(hash xor SIZE_SALT)) < params.largeShare * outcrop.richness * inward
    val heightRoll = GenRng.unit(GenRng.mix64(hash xor HEIGHT_SALT))
    val full = params.minHeight + heightRoll * (params.maxHeight - params.minHeight)

    return Shard(shardX, shardY, if (large) full else full * SMALL_HEIGHT_SHARE, large)
  }

  /**
   * Whether the ground here is ground a shard can break through.
   *
   * `CrystalScatter.standsOn`'s test, and it earns its keep on the very worlds that motivated this: the survey
   * found corrupted bodies under ice sheets on four of six seeds, and a shard on an ice cap would be a mineral
   * standing on a glacier. Asked of the cap *block* rather than of the temperature, because the one place that
   * knows what the top of a column is made of should decide what can come out of it.
   */
  private fun standsOn(x: Double, y: Double, ground: Double): Boolean {
    if (surface.waterLevelAt(x, y) > ground) return false

    val cap = SurfaceCover.cap(
      surface.biomeAt(x, y),
      surface.temperatureAt(x, y),
      0.0,
      // True by construction here; see the flags in `propsIn`.
      blighted = true
    )

    return cap != BlockType.ICE && cap != BlockType.SNOW
  }

  private fun claimed(into: PropInstances, from: Int, identity: Long): Boolean {
    for (i in from until into.count) if (into.identityAt(i) == identity) return true
    return false
  }

  private fun columnOf(world: Double): Long = Math.floorDiv(Quantize.toFixed(world), voxelUnits)

  private class Shard(val x: Double, val y: Double, val height: Double, val large: Boolean)

  private fun jitter(hash: Long, salt: Long): Double =
    (GenRng.unit(GenRng.mix64(hash + salt)) - 0.5) * params.jitterShare

  private companion object {
    const val SCATTER_SALT = 0x4165746865723031L
    const val PRESENCE_SALT = 0x2545F4914F6CDD1DL
    const val SIZE_SALT = 0x1E3779B97F4A7C15L
    const val HEIGHT_SALT = 0x3F58476D1CE4E5B9L

    const val JITTER_X_SALT = 0x23L
    const val JITTER_Y_SALT = 0x31L

    /** How tall a small shard is against a large one. */
    const val SMALL_HEIGHT_SHARE = 0.5
  }
}
