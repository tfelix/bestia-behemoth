package net.bestia.worldgen.viewer

import net.bestia.worldgen.core.BaseHeightField
import net.bestia.worldgen.core.ChunkColumnSource
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.ColumnHeights
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerData
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.ChunkMaterializer
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Anything the viewer can draw: a scalar over world space, plus how to colour it.
 *
 * Rasters, the base heightfield and the actual generated chunk columns all reduce to this, which is
 * the point - flipping between "what the 1 km raster says" and "what the chunk pipeline actually
 * produced" is one keypress and one code path, so a discrepancy between the two is something you
 * can see rather than something you have to suspect.
 *
 * Implementations must be safe to call from any thread; the viewer renders off the UI thread.
 */
interface ScalarField {

  val name: String

  /** Shown after the value in the probe readout. */
  val unit: String get() = ""

  val palette: Palette

  /** The value at a world position in metres, or [Double.NaN] where the field has none. */
  fun valueAt(worldX: Double, worldY: Double): Double

  /**
   * Null when this field can be rendered for [view], otherwise why it cannot - shown in the status
   * bar instead of the map.
   *
   * The chunk view is the reason this exists: sampling it while zoomed out to a continent would
   * ask the pipeline for millions of chunks, and a debug tool that hangs is a debug tool nobody
   * opens.
   */
  fun availabilityFor(view: Viewport): String? = null

  fun format(value: Double): String = when {
    value.isNaN() -> "-"
    abs(value) >= 1000.0 -> "%,.0f".format(value)
    abs(value) >= 10.0 -> "%.1f".format(value)
    else -> "%.3f".format(value)
  }
}

/**
 * A field that composes its own colour out of more than one input.
 *
 * The escape hatch from "one number through one palette", and it exists for exactly one kind of view: a map
 * of what is *there*, which no single raster holds. Land cover is in the biome layer, sea depth is in the
 * elevation layer, lake depth is the difference between two layers, and any one of them alone is a picture of
 * a component rather than of the world - see [WorldMapField].
 *
 * [ScalarField.valueAt] still has to return something meaningful, because relief shading and the cursor
 * readout are computed from it. The contract is therefore: `valueAt` is the height of the surface being
 * coloured, `rgbAt` is what that surface looks like.
 */
interface CompositeField : ScalarField {

  /**
   * The colour of one position.
   *
   * @param value this field's own [ScalarField.valueAt] at the same position, already sampled by the
   *   renderer. Passed in rather than re-sampled because every pixel needs both and sampling is the
   *   expensive half.
   */
  fun rgbAt(worldX: Double, worldY: Double, value: Double): Int
}

/**
 * A field whose availability is limited by how many chunks it would have to generate for one frame.
 *
 * The ceiling is adjustable because the two callers have completely different deadlines: the interactive
 * viewer has to finish between two frames of a drag, and an export has no deadline at all. Holding the
 * export to the interactive budget is what used to force the voxel views down to half a metre per pixel -
 * a sub-voxel picture of a quarter of the area, when one pixel per voxel was the entire point.
 */
interface ChunkBudgeted {

  /** Generating more than this many chunks for one frame is a hang, not a render. */
  var chunkBudget: Int
}

/** How a raster is sampled between cell centres. */
enum class Interpolation {
  /** Shows the true cell structure. Use it to see whether a raster stage is doing what you think. */
  NEAREST,
  BILINEAR,

  /** What chunk generation uses; what the terrain will actually be lifted through. */
  BICUBIC
}

/** A [FloatLayer] as a field. */
class FloatLayerField(
  private val layer: FloatLayer,
  override val palette: Palette,
  override val unit: String = "",
  val interpolation: Interpolation = Interpolation.BICUBIC,
  override val name: String = layer.id.name
) : ScalarField {

  override fun valueAt(worldX: Double, worldY: Double): Double {
    val metresPerCell = layer.region.resolution.metresPerCell
    val cellX = floor(worldX / metresPerCell).toInt()
    val cellY = floor(worldY / metresPerCell).toInt()

    // Outside the raster entirely: report no value rather than the clamped edge, so a stage that
    // wrote a region smaller than it claimed is visible as a hole instead of as smearing.
    if (!layer.region.contains(cellX, cellY)) return Double.NaN

    return when (interpolation) {
      Interpolation.NEAREST -> layer[cellX, cellY].toDouble()
      Interpolation.BILINEAR -> layer.sampleBilinear(worldX, worldY)
      Interpolation.BICUBIC -> layer.sampleBicubic(worldX, worldY)
    }
  }

  fun with(interpolation: Interpolation) =
    FloatLayerField(layer, palette, unit, interpolation, name)
}

/**
 * An [IntLayer] as a field. Always nearest-cell: interpolating a category id is meaningless.
 *
 * @param labels what an id means in words, or null where an id is genuinely just a number. See [Labels].
 */
class IntLayerField(
  private val layer: IntLayer,
  override val palette: Palette = CategoryPalette(),
  override val name: String = layer.id.name,
  private val labels: ((Int) -> String?)? = null
) : ScalarField {

  override fun valueAt(worldX: Double, worldY: Double): Double {
    val metresPerCell = layer.region.resolution.metresPerCell
    val cellX = floor(worldX / metresPerCell).toInt()
    val cellY = floor(worldY / metresPerCell).toInt()

    if (!layer.region.contains(cellX, cellY)) return Double.NaN

    return layer[cellX, cellY].toDouble()
  }

  /**
   * The id's name where there is one, otherwise the id.
   *
   * Same three-part shape as [ChunkSurfaceField.format], and for the same reason: an id this build does not
   * recognise has to stay readable as a number rather than becoming a question mark, because that is exactly
   * the case where you need to know which number it was.
   */
  override fun format(value: Double): String {
    if (value.isNaN()) return "-"
    val id = value.roundToInt()
    return labels?.invoke(id) ?: id.toString()
  }
}

/** The continuous base heightfield, before any vector feature - what features blend against. */
class BaseHeightFieldView(
  private val base: BaseHeightField,
  override val palette: Palette,
  override val name: String = "base height",
  override val unit: String = "m"
) : ScalarField {

  override fun valueAt(worldX: Double, worldY: Double) = base.heightAt(worldX, worldY)
}

/**
 * The heights the chunk pipeline actually produces, features and all.
 *
 * This is the view that matters. Everything else in the viewer shows an input; this shows the
 * output, at voxel resolution, through the same [ChunkColumnSource] the server would use. If a
 * river reads correctly in the raster view and wrong here, the bug is in the vector tier.
 */
class ChunkHeightField(
  private val config: WorldConfig,
  private val source: ChunkColumnSource,
  override val palette: Palette,
  override val name: String = "chunk heights",
  override val unit: String = "m",
  override var chunkBudget: Int = 4096
) : ScalarField, ChunkBudgeted {

  /** Access-ordered, so panning evicts the chunks you have left rather than the ones on screen. */
  private val cache = object : LinkedHashMap<ChunkPos, ColumnHeights>(256, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ChunkPos, ColumnHeights>) =
      size > CACHE_SIZE
  }

  override fun availabilityFor(view: Viewport): String? {
    val needed = chunksIn(view)
    return if (needed <= chunkBudget) {
      null
    } else {
      "zoom in to generate chunks - this view spans ${"%,d".format(needed)} chunks, budget $chunkBudget"
    }
  }

  override fun valueAt(worldX: Double, worldY: Double): Double {
    val chunkX = floor(worldX / config.chunkExtent).toInt()
    val chunkY = floor(worldY / config.chunkExtent).toInt()
    val heights = heightsOf(ChunkPos(chunkX, chunkY))

    val localX = floor((worldX - chunkX * config.chunkExtent) / config.voxelSize).toInt()
    val localY = floor((worldY - chunkY * config.chunkExtent) / config.voxelSize).toInt()

    return heights[
      localX.coerceIn(0, config.chunkSize - 1),
      localY.coerceIn(0, config.chunkSize - 1)
    ]
  }

  /** Chunks generated so far, for the status bar - a cheap sanity check on the cache. */
  @Synchronized
  fun cachedChunks() = cache.size

  @Synchronized
  private fun heightsOf(chunk: ChunkPos): ColumnHeights =
    cache.getOrPut(chunk) { source.heights(chunk, 0) }

  private fun chunksIn(view: Viewport): Int {
    val bounds = view.bounds
    val across = bounds.width / config.chunkExtent + 1.0
    val down = bounds.height / config.chunkExtent + 1.0
    return (across * down).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
  }

  private companion object {
    const val CACHE_SIZE = 8192
  }
}

/**
 * The topmost non-air block of every column, as produced by the real materialiser.
 *
 * The last view in the chain, and the only one that shows what a player would actually stand on. Every
 * other field shows an input or an intermediate; this shows blocks. It is what catches the class of bug
 * that height views structurally cannot - grass under water, bedrock where soil should be, a beach on
 * the inland side of a coast - because height is continuous and hides category mistakes completely.
 */
class ChunkSurfaceField(
  private val config: WorldConfig,
  private val materializer: ChunkMaterializer,
  override val palette: Palette = BlockPalette(),
  override val name: String = "surface block",
  override var chunkBudget: Int = 1024
) : ScalarField, ChunkBudgeted {

  /** Access-ordered, so panning evicts the chunks you have left rather than the ones on screen. */
  private val cache = object : LinkedHashMap<Long, IntArray>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, IntArray>) = size > CACHE_SIZE
  }

  override fun availabilityFor(view: Viewport): String? {
    val bounds = view.bounds
    val across = bounds.width / config.chunkExtent + 1.0
    val down = bounds.height / config.chunkExtent + 1.0
    val needed = (across * down).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()

    return if (needed <= chunkBudget) {
      null
    } else {
      "zoom in to materialise chunks - this view spans ${"%,d".format(needed)} chunks, budget $chunkBudget"
    }
  }

  override fun valueAt(worldX: Double, worldY: Double): Double {
    val chunkX = floor(worldX / config.chunkExtent).toInt()
    val chunkY = floor(worldY / config.chunkExtent).toInt()
    val surface = surfaceOf(chunkX, chunkY)

    val localX = floor((worldX - chunkX * config.chunkExtent) / config.voxelSize)
      .toInt().coerceIn(0, config.chunkSize - 1)
    val localY = floor((worldY - chunkY * config.chunkExtent) / config.voxelSize)
      .toInt().coerceIn(0, config.chunkSize - 1)

    val block = surface[localY * config.chunkSize + localX]
    // A column whose surface fell outside the vertical chunk that was materialised has no answer here,
    // and saying so is better than reporting the air above or the rock below it.
    return if (block < 0) Double.NaN else block.toDouble()
  }

  override fun format(value: Double): String =
    if (value.isNaN()) "-" else BlockType.ofOrNull(value.toInt())?.name?.lowercase() ?: "?"

  @Synchronized
  fun cachedChunks() = cache.size

  @Synchronized
  private fun surfaceOf(chunkX: Int, chunkY: Int): IntArray =
    cache.getOrPut((chunkX.toLong() shl 32) or (chunkY.toLong() and 0xFFFFFFFFL)) {
      // surfaceColumns rather than materializeSurface: the latter reads whatever is at the top of one
      // grid-aligned slab, which for a chunk whose relief straddles a vertical boundary is the slab ceiling
      // rather than the ground - so this view used to show bedrock on ridges and call it the surface.
      materializer.surfaceColumns(chunkX, chunkY).block
    }

  private companion object {
    const val CACHE_SIZE = 2048
  }
}

/**
 * How full the topmost voxel of each column is, in `[0,1]`.
 *
 * The view that makes occupancy visible, and the only one that can tell whether the sub-voxel precision the
 * pipeline computes is actually reaching the voxels. On a slope it should read as a sawtooth: the fill climbs
 * from nothing to full, then resets as the surface crosses into the next voxel up. Flat bands of exactly 1.0
 * over sloping ground would mean the fraction is being discarded somewhere and the terrain has gone back to
 * metre stair-steps, which is precisely the regression that is otherwise invisible - the block view and the
 * height view both look identical either way.
 */
class SurfaceOccupancyField(
  private val config: WorldConfig,
  private val materializer: ChunkMaterializer,
  // Not shadeable: this is a sawtooth over a slope, not a height, and hillshading it would read as relief.
  override val palette: Palette = ContinuousPalette(Ramps.VIRIDIS, 0.0..1.0, shadeable = false),
  override val name: String = "surface fill",
  override var chunkBudget: Int = 1024
) : ScalarField, ChunkBudgeted {

  override val unit get() = "of a voxel"

  private val cache = object : LinkedHashMap<Long, DoubleArray>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, DoubleArray>) = size > CACHE_SIZE
  }

  override fun availabilityFor(view: Viewport): String? {
    val bounds = view.bounds
    val across = bounds.width / config.chunkExtent + 1.0
    val down = bounds.height / config.chunkExtent + 1.0
    val needed = (across * down).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()

    return if (needed <= chunkBudget) {
      null
    } else {
      "zoom in to materialise chunks - this view spans ${"%,d".format(needed)} chunks, budget $chunkBudget"
    }
  }

  override fun valueAt(worldX: Double, worldY: Double): Double {
    val chunkX = floor(worldX / config.chunkExtent).toInt()
    val chunkY = floor(worldY / config.chunkExtent).toInt()
    val fills = fillsOf(chunkX, chunkY)

    val localX = floor((worldX - chunkX * config.chunkExtent) / config.voxelSize)
      .toInt().coerceIn(0, config.chunkSize - 1)
    val localY = floor((worldY - chunkY * config.chunkExtent) / config.voxelSize)
      .toInt().coerceIn(0, config.chunkSize - 1)

    return fills[localY * config.chunkSize + localX]
  }

  override fun format(value: Double) = if (value.isNaN()) "-" else "%.3f".format(value)

  @Synchronized
  private fun fillsOf(chunkX: Int, chunkY: Int): DoubleArray =
    cache.getOrPut((chunkX.toLong() shl 32) or (chunkY.toLong() and 0xFFFFFFFFL)) {
      // NO_FILL where the air interface is above what was materialised - a deep ocean column is water for
      // hundreds of metres, and its ceiling voxel reads a completely truthful 1.0 that means something
      // entirely different from "the surface landed on a voxel boundary". No answer beats a number that looks
      // comparable with its neighbours and is not.
      materializer.surfaceColumns(chunkX, chunkY).fill
    }

  private companion object {
    const val CACHE_SIZE = 2048
  }
}

/**
 * Log-scales another field.
 *
 * Flow accumulation spans six orders of magnitude - a linear ramp shows one bright pixel at the
 * river mouth and black everywhere else, which is exactly the field you most need to look at.
 */
class LogScaledField(
  private val delegate: ScalarField,
  override val palette: Palette = ContinuousPalette(Ramps.PRECIPITATION),
  override val name: String = "log ${delegate.name}"
) : ScalarField {

  override val unit get() = "log ${delegate.unit}".trim()

  override fun valueAt(worldX: Double, worldY: Double): Double {
    val v = delegate.valueAt(worldX, worldY)
    return if (v.isNaN()) Double.NaN else ln(1.0 + maxOf(0.0, v))
  }

  override fun availabilityFor(view: Viewport) = delegate.availabilityFor(view)
}

/**
 * The signed difference between two fields.
 *
 * Built for one job: subtract the raster elevation from the generated chunk heights and see what
 * the vector tier and the detail noise actually did, rather than inferring it from two pictures.
 */
class DifferenceField(
  private val minuend: ScalarField,
  private val subtrahend: ScalarField,
  override val palette: Palette = ContinuousPalette(Ramps.DIVERGING, -40.0..40.0),
  override val name: String = "${minuend.name} - ${subtrahend.name}",
  override val unit: String = minuend.unit
) : ScalarField {

  override fun valueAt(worldX: Double, worldY: Double): Double {
    val a = minuend.valueAt(worldX, worldY)
    val b = subtrahend.valueAt(worldX, worldY)
    return if (a.isNaN() || b.isNaN()) Double.NaN else a - b
  }

  override fun availabilityFor(view: Viewport) =
    minuend.availabilityFor(view) ?: subtrahend.availabilityFor(view)
}

/** Wraps a [LayerData] in the matching field type with a default palette. */
fun LayerData.asField(seaLevel: Double = 0.0): ScalarField = when (this) {
  is FloatLayer -> FloatLayerField(
    layer = this,
    palette = Palettes.forLayer(id, seaLevel),
    unit = if (id == LayerId.ELEVATION) "m" else ""
  )

  is IntLayer -> IntLayerField(this, Palettes.forLayer(id, seaLevel), labels = Labels.forLayer(id))
}
