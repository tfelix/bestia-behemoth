package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.dress.CemeteryChannels
import net.bestia.worldgen.dress.CropStyle
import net.bestia.worldgen.dress.FenceStyle
import net.bestia.worldgen.dress.FixtureChannels
import net.bestia.worldgen.dress.FixtureKind
import net.bestia.worldgen.dress.Plots
import net.bestia.worldgen.civ.FieldChannels
import net.bestia.worldgen.history.SiteChannels
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The fixtures standing in one chunk: what the dressing stage placed, and what fields and cemeteries imply.
 *
 * Two sources, for the split `dress/DressingStage` explains. A `FIXTURE` marker becomes exactly one prop where it
 * stands. A `FIELD` is fenced along every edge, tiled with its crop, and given a haystack or three and the odd
 * scarecrow; a `CEMETERY` is walled and filled with headstones on the plots its named graves left free; a
 * `BATTLEFIELD` is strewn with what its dead left behind. All of that is a pure function of the feature, so any chunk the feature reaches derives the same props and the one that
 * owns each prop's position emits it.
 *
 * ### Ground
 *
 * A fixture stands on the `site` ground, so it inherits every veto the trees have: nothing on a road's tread, in a
 * building or over a cave mouth. A fence run is refused if either end or its middle would be, so a run never
 * crosses a road. The two exceptions are the marker that carries its own elevation - a pier's deck, a moored boat
 * - which stand on the water at that height instead.
 *
 * ### Names
 *
 * A metre lattice, as a building's: `PropId` names, it does not space. Two fixtures in one metre cell would share
 * a name, so the second in a chunk is dropped - every derivation here keeps far more than a metre apart, and the
 * guard is for the coincidence.
 */
class FixtureProps(
  private val config: WorldConfig,
  private val surface: SurfaceSampler,
  features: List<VectorFeature>
) {

  private val markers = features.filter { it.kind == FeatureKind.FIXTURE }.filterIsInstance<PointMarker>()
  private val fields = features.filter { it.kind == FeatureKind.FIELD }.filterIsInstance<AreaFeature>()
  private val cemeteries = features.filter { it.kind == FeatureKind.CEMETERY }.filterIsInstance<AreaFeature>()
  private val battlefields = features.filter { it.kind == FeatureKind.BATTLEFIELD }.filterIsInstance<PointMarker>()

  val isEmpty get() = markers.isEmpty() && fields.isEmpty() && cemeteries.isEmpty() && battlefields.isEmpty()

  fun propsIn(chunk: ChunkPos, site: PropSite, into: PropInstances) {
    if (isEmpty) return
    val emitted = HashSet<Long>()

    // `elevation` is the fixture's own height when it has one, else NaN to stand it on the ground here. The ground
    // is asked only once the prop is known to be this chunk's: `site` answers for this chunk's columns alone.
    fun emit(kind: FixtureKind, x: Double, y: Double, elevation: Double, yaw: Double, halfLength: Double, halfWidth: Double,
             height: Double, style: Int) {
      if (config.chunkOf(x) != chunk.x || config.chunkOf(y) != chunk.y) return
      val ground = if (elevation.isFinite()) elevation else site.groundAt(x, y)
      if (ground.isNaN()) return
      val id = PropId.of(PropKind.FIXTURE, BuildingProps.cellOf(x), BuildingProps.cellOf(y))
      if (!emitted.add(id)) return
      into.add(
        kind = PropKind.FIXTURE,
        identity = id,
        x = x,
        y = y,
        ground = ground,
        heightM = height,
        radiusM = halfLength,
        halfWidthM = halfWidth,
        yawRad = yaw,
        flags = PropFlags.fixture(style.coerceIn(0, MAX_STYLE)),
        subKind = kind.ordinal
      )
    }

    for (marker in markers) {
      val x = marker.position.x
      val y = marker.position.y
      if (config.chunkOf(x) != chunk.x || config.chunkOf(y) != chunk.y) continue
      val kind = runCatching { FixtureKind.entries[marker.attribute(FixtureChannels.KIND).toInt()] }.getOrNull() ?: continue
      emit(
        kind, x, y, marker.attribute(FixtureChannels.ELEVATION),
        yaw = marker.attribute(FixtureChannels.YAW),
        halfLength = marker.attribute(FixtureChannels.HALF_LENGTH),
        halfWidth = marker.attribute(FixtureChannels.HALF_WIDTH),
        height = marker.attribute(FixtureChannels.HEIGHT),
        style = marker.attribute(FixtureChannels.STYLE).toInt()
      )
    }

    val bounds = config.chunkBounds(chunk)
    for (field in fields) {
      if (!field.bbox.intersects(bounds)) continue
      val centre = Vec2d(field.ring.bbox.centerX, field.ring.bbox.centerY)
      val biome = surface.biomeAt(centre.x, centre.y)
      val h = GenRng.hash(config.seed, field.id.value, FIELD_SALT)

      val fence = fenceStyleFor(biome, h)
      fenceRing(chunk, field, fence, gateEdge = (GenRng.unit(GenRng.mix64(h + 1)) * field.ring.vertexCount).toInt(), site, ::emit)

      val crop = cropFor(biome, h) ?: continue
      val bearing = runCatching { field.perimeter?.let { it.valueAt(it.channel(FieldChannels.BEARING), 0) } }.getOrNull() ?: 0.0
      crops(field, crop, bearing, bounds, site, ::emit)
      if (crop != CropStyle.CABBAGE) haystacksAndScarecrow(field, h, site, ::emit)
    }

    for (cemetery in cemeteries) {
      if (!cemetery.bbox.intersects(bounds)) continue
      val table = cemetery.perimeter ?: continue
      fun channel(name: String) = runCatching { table.valueAt(table.channel(name), 0) }.getOrNull()
      val yaw = channel(CemeteryChannels.YAW) ?: continue
      val halfLength = channel(CemeteryChannels.HALF_LENGTH) ?: continue
      val halfWidth = channel(CemeteryChannels.HALF_WIDTH) ?: continue
      val named = channel(CemeteryChannels.NAMED)?.toInt() ?: 0
      val fill = channel(CemeteryChannels.FILL) ?: 0.5
      val wall = FenceStyle.entries.getOrNull(channel(CemeteryChannels.WALL)?.toInt() ?: 0) ?: FenceStyle.DRY_STONE
      val centre = Vec2d(
        cemetery.ring.vertices.sumOf { it.x } / cemetery.ring.vertexCount,
        cemetery.ring.vertices.sumOf { it.y } / cemetery.ring.vertexCount
      )

      // The near wall - from the last corner back to the first, at +along - is the one facing the settlement.
      fenceRing(chunk, cemetery, wall, gateEdge = cemetery.ring.vertexCount - 1, site, ::emit)

      val plots = Plots.cemetery(centre, yaw, halfLength, halfWidth)
      for (k in named until plots.size) {
        val plot = plots[k]
        val roll = GenRng.hashUnit(config.seed, cemetery.id.value, k.toLong(), GRAVE_SALT)
        if (roll >= fill) continue
        val lean = (GenRng.hashUnit(config.seed, cemetery.id.value, k.toLong(), LEAN_SALT) - 0.5) * 0.3
        emit(
          FixtureKind.GRAVESTONE, plot.x, plot.y, Double.NaN, yaw + lean, 0.35, 0.1,
          FixtureKind.GRAVESTONE.heightM * (0.8 + 0.4 * roll / fill), (roll * 1000).toInt() % GRAVE_MODELS
        )
      }
    }

    for (field in battlefields) {
      val radius = field.attribute(SiteChannels.RADIUS)
      val reach = Aabb(field.position.x - radius, field.position.y - radius, field.position.x + radius, field.position.y + radius)
      if (reach.intersects(bounds)) battlefieldRemains(field, radius, ::emit)
    }
  }

  /**
   * Bones and broken arms over a battlefield: more where more fell, fewer the older the field is. Where a hero
   * slew a world boss, its skeleton lies at the heart of the field.
   */
  private fun battlefieldRemains(field: PointMarker, radius: Double, emit: Emit) {
    val age = field.attribute(SiteChannels.DECAY).coerceIn(0.0, 1.0)
    val h = GenRng.hash(config.seed, field.id.value, REMAINS_SALT)

    if (field.attribute(SiteChannels.MONSTER) >= 0) {
      emit.emit(FixtureKind.GIANT_REMAINS, field.position.x, field.position.y, Double.NaN, GenRng.unit(h) * 2.0 * PI,
        GIANT_HALF_LENGTH, GIANT_HALF_WIDTH, FixtureKind.GIANT_REMAINS.heightM, 0)
    }

    val left = field.attribute(SiteChannels.FALLEN) * REMAINS_PER_DEAD * (1.0 - AGE_THINNING * age)
    for (k in 0 until left.toInt().coerceAtMost(MAX_REMAINS)) {
      val piece = GenRng.hash(h, k.toLong())
      val out = radius * REMAINS_SPREAD * sqrt(GenRng.unit(GenRng.mix64(piece + 1)))
      val angle = 2.0 * PI * GenRng.unit(GenRng.mix64(piece + 2))
      val kind = remainsKindOf(GenRng.unit(piece), age)
      emit.emit(
        kind, field.position.x + out * cos(angle), field.position.y + out * sin(angle), Double.NaN,
        yaw = 2.0 * PI * GenRng.unit(GenRng.mix64(piece + 3)), halfLength = 0.5, halfWidth = 0.3, kind.heightM, 0
      )
    }
  }

  private fun remainsKindOf(roll: Double, age: Double): FixtureKind {
    return when {
      roll < BANNER_SHARE && age < BANNER_ROTS_AT -> FixtureKind.BANNER_RAG
      roll < 0.45 -> FixtureKind.BONES
      roll < 0.75 -> FixtureKind.RUSTED_BLADE
      else -> FixtureKind.BROKEN_SHIELD
    }
  }

  private fun interface Emit {
    fun emit(kind: FixtureKind, x: Double, y: Double, ground: Double, yaw: Double, halfLength: Double,
             halfWidth: Double, height: Double, style: Int)
  }

  /**
   * One run of fence per stretch of each ring edge, at most [MAX_RUN] long, with a gap for a gate in the middle
   * of [gateEdge].
   */
  private fun fenceRing(chunk: ChunkPos, area: AreaFeature, style: FenceStyle, gateEdge: Int, site: PropSite, emit: Emit) {
    val vertices = area.ring.vertices
    val height = when (style) {
      FenceStyle.HEDGE -> 1.6
      FenceStyle.DRY_STONE -> 1.0
      else -> FixtureKind.FENCE.heightM
    }
    val halfWidth = when (style) {
      FenceStyle.HEDGE -> 0.6
      FenceStyle.DRY_STONE -> 0.35
      else -> 0.1
    }
    for (i in vertices.indices) {
      val a = vertices[i]
      val b = vertices[(i + 1) % vertices.size]
      val edge = b - a
      val length = edge.length
      if (length < MIN_EDGE) continue
      val dir = edge * (1.0 / length)
      val yaw = atan2(dir.y, dir.x)

      val stretches = if (i == gateEdge && length > GATE_GAP + 2.0 * MIN_EDGE) {
        val half = (length - GATE_GAP) * 0.5
        listOf(0.0 to half, half + GATE_GAP to length)
      } else {
        listOf(0.0 to length)
      }

      for ((from, until) in stretches) {
        val span = until - from
        val runs = max(1, ceil(span / MAX_RUN).toInt())
        val runLength = span / runs
        for (r in 0 until runs) {
          val start = a + dir * (from + r * runLength)
          val end = a + dir * (from + (r + 1) * runLength)
          val mid = (start + end) * 0.5
          // A run whose end lands on a road is dropped, so a fence never crosses one. Only an end in this chunk can
          // be asked; the run is owned by its middle, and an end in the next chunk is that chunk's ground.
          if (blockedAt(chunk, site, start) || blockedAt(chunk, site, end)) continue
          emit.emit(FixtureKind.FENCE, mid.x, mid.y, Double.NaN, yaw, runLength * 0.5, halfWidth,
            height, style.ordinal)
        }
      }
    }
  }

  /** The crop tiles of one field that this chunk owns, laid on a grid along the furrows. */
  private fun crops(field: AreaFeature, crop: CropStyle, bearing: Double, bounds: net.bestia.worldgen.vector.Aabb,
                    site: PropSite, emit: Emit) {
    val along = Vec2d(cos(bearing), sin(bearing))
    val across = Vec2d(-along.y, along.x)
    val corners = listOf(
      Vec2d(bounds.minX, bounds.minY), Vec2d(bounds.maxX, bounds.minY),
      Vec2d(bounds.minX, bounds.maxY), Vec2d(bounds.maxX, bounds.maxY)
    )
    val us = corners.map { it.x * along.x + it.y * along.y }
    val vs = corners.map { it.x * across.x + it.y * across.y }
    val fromU = floor(us.min() / CROP_TILE).toLong()
    val untilU = floor(us.max() / CROP_TILE).toLong()
    val fromV = floor(vs.min() / CROP_TILE).toLong()
    val untilV = floor(vs.max() / CROP_TILE).toLong()

    for (iu in fromU..untilU) {
      for (iv in fromV..untilV) {
        val centre = along * ((iu + 0.5) * CROP_TILE) + across * ((iv + 0.5) * CROP_TILE)
        if (!field.contains(centre.x, centre.y)) continue
        if (field.ring.project(centre).distance < CROP_INSET) continue
        emit.emit(FixtureKind.CROP, centre.x, centre.y, Double.NaN, bearing,
          CROP_TILE * 0.5, CROP_TILE * 0.5, FixtureKind.CROP.heightM, crop.ordinal)
      }
    }
  }

  /** Up to three haystacks just inside a grain field's edge, and a scarecrow in the middle of some of them. */
  private fun haystacksAndScarecrow(field: AreaFeature, h: Long, site: PropSite, emit: Emit) {
    val vertices = field.ring.vertices
    val stacks = (GenRng.unit(GenRng.mix64(h + 2)) * 4.0).toInt()
    for (k in 0 until stacks) {
      val i = (GenRng.unit(GenRng.mix64(h + 10 + k)) * vertices.size).toInt().coerceAtMost(vertices.size - 1)
      val a = vertices[i]
      val b = vertices[(i + 1) % vertices.size]
      val t = 0.2 + 0.6 * GenRng.unit(GenRng.mix64(h + 20 + k))
      val onEdge = a + (b - a) * t
      val dir = (b - a).let { it * (1.0 / max(1e-6, it.length)) }
      val normal = Vec2d(-dir.y, dir.x)
      val inward = if (field.contains(onEdge.x + normal.x * 2.0, onEdge.y + normal.y * 2.0)) normal else -normal
      val at = onEdge + inward * HAYSTACK_INSET
      if (!field.contains(at.x, at.y)) continue
      emit.emit(FixtureKind.HAYSTACK, at.x, at.y, Double.NaN, atan2(dir.y, dir.x), 1.4, 1.4,
        FixtureKind.HAYSTACK.heightM, k % 2)
    }

    if (GenRng.unit(GenRng.mix64(h + 3)) < SCARECROW_SHARE) {
      val centre = Vec2d(field.ring.bbox.centerX, field.ring.bbox.centerY)
      if (field.contains(centre.x, centre.y)) {
        // Off the crop tile's own centre, so the two never share a name.
        val at = centre + Vec2d(3.3, 2.1)
        emit.emit(FixtureKind.SCARECROW, at.x, at.y, Double.NaN, GenRng.unit(h) * 6.283, 0.0, 0.0,
          FixtureKind.SCARECROW.heightM, 0)
      }
    }
  }

  /** Whether [at] is in this chunk and refused by its ground - a road's tread, a building, a cave mouth. */
  private fun blockedAt(chunk: ChunkPos, site: PropSite, at: Vec2d): Boolean =
    config.chunkOf(at.x) == chunk.x && config.chunkOf(at.y) == chunk.y && site.groundAt(at.x, at.y).isNaN()

  private fun fenceStyleFor(biome: Biome, h: Long): FenceStyle = when (biome) {
    Biome.DRYLAND, Biome.DESERT, Biome.BADLANDS, Biome.TUNDRA, Biome.ALPINE, Biome.COLD_DESERT -> FenceStyle.DRY_STONE
    Biome.TEMPERATE_FOREST, Biome.TEMPERATE_RAINFOREST -> FenceStyle.HEDGE
    else -> if (GenRng.unit(GenRng.mix64(h + 4)) < WATTLE_SHARE) FenceStyle.WATTLE else FenceStyle.POST_AND_RAIL
  }

  /** What a field grows, or null for one lying fallow this year. */
  private fun cropFor(biome: Biome, h: Long): CropStyle? {
    val roll = GenRng.unit(GenRng.mix64(h + 5))
    if (roll < FALLOW_SHARE) return null
    if (biome == Biome.TUNDRA || biome == Biome.TAIGA || biome == Biome.ALPINE) return CropStyle.BARLEY
    return when {
      roll < 0.6 -> CropStyle.WHEAT
      roll < 0.8 -> CropStyle.BARLEY
      else -> CropStyle.CABBAGE
    }
  }

  private companion object {
    const val MAX_STYLE = 31
    const val MIN_EDGE = 4.0
    const val MAX_RUN = 30.0
    const val GATE_GAP = 4.0
    const val CROP_TILE = 32.0
    const val CROP_INSET = 6.0
    const val HAYSTACK_INSET = 5.0
    const val SCARECROW_SHARE = 0.35
    const val WATTLE_SHARE = 0.2
    const val FALLOW_SHARE = 0.15
    const val GRAVE_MODELS = 3

    /** Pieces of remains per dead on a fresh field, and the share of them a fully decayed field has lost. */
    const val REMAINS_PER_DEAD = 0.15
    const val AGE_THINNING = 0.8
    const val MAX_REMAINS = 120
    const val REMAINS_SPREAD = 0.85
    const val BANNER_SHARE = 0.15
    const val BANNER_ROTS_AT = 0.5
    const val GIANT_HALF_LENGTH = 6.0
    const val GIANT_HALF_WIDTH = 3.0

    const val FIELD_SALT = 0x4649454C44L
    const val GRAVE_SALT = 0x4752415645L
    const val LEAN_SALT = 0x4C45414EL
    const val REMAINS_SALT = 0x52454D41L
  }
}
