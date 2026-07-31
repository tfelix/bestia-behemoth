package net.bestia.worldgen.viewer

import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.ChunkSeamCheck
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.VectorFeature
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** What to draw on top of the field, and how. */
data class RenderOptions(
  val hillshade: Boolean = true,
  val exaggeration: Double = 2.0,
  val features: Boolean = true,

  /**
   * Which feature kinds to draw, or null for all of them.
   *
   * Separate from [features] because the overlay stopped being one thing. A world with town layout in it emits
   * several thousand buildings and streets, every one of them smaller than a pixel at world scale, and they
   * bury the rivers and roads that the overlay is worth having for. All-or-nothing meant the only way to read
   * the map was to turn the whole overlay off.
   */
  val featureKinds: Set<FeatureKind>? = null,

  /**
   * The world raster's cell grid - [WorldConfig.baseResolution], one line per kilometre on most worlds.
   *
   * This is the grid every field map is *sampled from*, and the coarseness the whole vector tier exists to
   * escape. One trap: it always draws the world's base resolution, so while a coarser layer is showing -
   * climate runs four times coarser - it is not that layer's own cell grid.
   */
  val cellGrid: Boolean = false,

  /**
   * The voxel-chunk tiling - [WorldConfig.chunkExtent], 32 m where a chunk is 32 voxels of a metre.
   *
   * What the materialiser generates and caches in, and exactly the set of lines the `S` seam check tests for
   * disagreement, so a red seam marker can only ever appear on one of them.
   */
  val chunkGrid: Boolean = false,

  /** Stretch the palette to the values actually on screen, ignoring its declared range. */
  val autoRange: Boolean = false,
  /** Draw markers where [ChunkSeamCheck] found disagreeing columns. */
  val seams: List<ChunkSeamCheck.Seam> = emptyList()
) {

  fun draws(kind: FeatureKind) = features && (featureKinds == null || kind in featureKinds)

  companion object {

    /**
     * Kinds not worth drawing until you have zoomed in far enough to ask for them.
     *
     * Two different reasons, and both are about ink that carries no information at map scale.
     *
     * `BUILDING`, `STREET` and `BUSINESS` are step 8's output: several thousand features per world, each a few
     * metres across, so at any zoom where a world fits on screen they are a grey wash over the rivers and
     * roads the overlay exists for.
     *
     * `SETTLEMENT_HISTORY` and `SETTLEMENT_ECONOMY` are worse than noise - they are **duplicates**. They are
     * attribute records pinned to a settlement's own coordinates, not places of their own, and their priority
     * puts them on top, so each one paints its dot squarely over the settlement dot underneath. What they know
     * reaches the map already: the settlement's size comes from history's population.
     */
    val HIDDEN_BY_DEFAULT = setOf(
      FeatureKind.BUILDING,
      FeatureKind.STREET,
      FeatureKind.BUSINESS,
      FeatureKind.SETTLEMENT_HISTORY,
      FeatureKind.SETTLEMENT_ECONOMY
    )
  }
}

/**
 * One rendered frame, together with the range the palette was actually stretched over.
 *
 * The range comes back rather than being hidden inside the renderer because a map without a legend
 * is decoration: "the mountains are white" means nothing until you know whether white is 900 m or
 * 9000 m.
 */
class RenderedMap(
  val image: BufferedImage,
  val field: ScalarField,
  val low: Double,
  val high: Double,
  /** Non-null when the field could not be evaluated for this view; the map is then blank. */
  val unavailable: String? = null,

  /**
   * For a categorical field, the ids actually on screen with their pixel counts, commonest first.
   *
   * Populated only when [Palette.categorical], because it is what the legend draws instead of a colour bar,
   * and it has to be measured here: the renderer is the only thing that has seen every sampled value, and
   * re-deriving it in the legend would mean sampling the field a second time.
   *
   * Counts are pixels, not cells - what share of the *view* each category covers, which is the question a
   * person reading a map asks.
   */
  val categories: List<Pair<Double, Int>> = emptyList()
)

/**
 * Renders a [ScalarField] through a [Viewport], with hillshading and vector overlays.
 *
 * Sampling is one field evaluation per pixel at the pixel's world centre - no smoothing, no
 * mipmapping. That is deliberate: this tool exists to show what the pipeline produces, and a
 * renderer that quietly filters the data is a renderer that hides the bug you opened it to find.
 */
class MapRenderer(
  private val config: WorldConfig,

  /**
   * How many people live at a settlement marker, for sizing its dot. Null where it cannot be known.
   *
   * Injected rather than read off the marker, because the number worth drawing is not the one the marker
   * carries: placement writes the population the *site* could support, and history writes what is actually
   * there now. Only [WorldScene] can perform that join, and a renderer should not learn how.
   *
   * The default reads the marker's own figure, so a caller with no scene still gets dots that mean something.
   */
  private val populationOf: (PointMarker) -> Double? = { it.optionalAttribute(SettlementChannels.POPULATION) }
) {

  fun render(
    field: ScalarField,
    view: Viewport,
    options: RenderOptions = RenderOptions(),
    features: List<VectorFeature> = emptyList()
  ): RenderedMap {
    val image = BufferedImage(view.widthPx, view.heightPx, BufferedImage.TYPE_INT_RGB)

    field.availabilityFor(view)?.let { reason ->
      fillBackground(image)
      return RenderedMap(image, field, 0.0, 0.0, reason)
    }

    val values = sample(field, view)
    val range = rangeOf(field, values, options.autoRange)
    val palette = field.palette.withRange(range.first, range.second)

    val pixels = (image.raster.dataBuffer as DataBufferInt).data
    if (field is CompositeField) {
      colorComposite(field, view, values, pixels)
    } else {
      for (i in values.indices) {
        pixels[i] = if (values[i].isNaN()) NO_DATA else palette.rgb(values[i])
      }
    }

    if (options.hillshade && field.palette.shadeable) {
      val shade = Hillshade.shade(
        values, view.widthPx, view.heightPx, view.metresPerPixel, options.exaggeration
      )
      for (i in pixels.indices) {
        if (!values[i].isNaN()) {
          pixels[i] = Colors.scale(pixels[i], shade[i])
        }
      }
    }

    drawOverlays(image, view, options, features)

    return RenderedMap(
      image, field, range.first, range.second,
      categories = if (field.palette.categorical) census(values) else emptyList()
    )
  }

  /**
   * Which category ids are on screen and how many pixels each covers, commonest first.
   *
   * Histogrammed into an array over the id span rather than a map, because this runs on every frame of a drag
   * over roughly a million samples, and a `HashMap<Double, Int>` boxes every one of them. Gives up on a span
   * too wide to be a category set - plate ids on a huge world - and the legend then simply has nothing to
   * draw, which is the right outcome for a field whose ids are not a vocabulary anyway.
   */
  private fun census(values: DoubleArray): List<Pair<Double, Int>> {
    var low = Int.MAX_VALUE
    var high = Int.MIN_VALUE
    for (v in values) {
      if (v.isNaN()) continue
      val id = v.toInt()
      if (id < low) low = id
      if (id > high) high = id
    }

    if (low > high || high - low + 1 > MAX_CATEGORIES) return emptyList()

    val counts = IntArray(high - low + 1)
    for (v in values) {
      if (!v.isNaN()) counts[v.toInt() - low]++
    }

    return counts.indices
      .filter { counts[it] > 0 }
      .sortedByDescending { counts[it] }
      .map { (it + low).toDouble() to counts[it] }
  }

  /**
   * Colours a [CompositeField], which decides its own pixel rather than going through a palette.
   *
   * The hillshade path above is untouched by this: it works off [values], which a composite still produces,
   * so relief shading applies to a composed map exactly as it does to a height field. That is the whole
   * reason the two are separate - a categorical *palette* cannot be shaded, but a categorical *colour over a
   * real surface* can be, and conflating the two is what left the biome map flat.
   */
  private fun colorComposite(
    field: CompositeField,
    view: Viewport,
    values: DoubleArray,
    pixels: IntArray
  ) {
    for (py in 0 until view.heightPx) {
      val worldY = view.worldY(py)
      val row = py * view.widthPx
      for (px in 0 until view.widthPx) {
        val i = row + px
        val value = values[i]
        pixels[i] = if (value.isNaN()) NO_DATA else field.rgbAt(view.worldX(px), worldY, value)
      }
    }
  }

  private fun sample(field: ScalarField, view: Viewport): DoubleArray {
    val values = DoubleArray(view.widthPx * view.heightPx)

    for (py in 0 until view.heightPx) {
      val worldY = view.worldY(py)
      val row = py * view.widthPx
      for (px in 0 until view.widthPx) {
        values[row + px] = field.valueAt(view.worldX(px), worldY)
      }
    }

    return values
  }

  /**
   * Auto-ranging clips at the 1st and 99th percentile rather than using the extremes, because one
   * NaN-adjacent outlier or a single un-eroded pit would otherwise flatten the whole map to one
   * colour - which reads as "the stage produced nothing" and sends you debugging the wrong thing.
   */
  private fun rangeOf(field: ScalarField, values: DoubleArray, auto: Boolean): Pair<Double, Double> {
    val declared = field.palette.range
    if (!auto && declared != null) return declared.start to declared.endInclusive

    val present = values.filter { !it.isNaN() }.sorted()
    if (present.isEmpty()) return 0.0 to 1.0

    val low = present[(present.size * 0.01).toInt().coerceIn(0, present.size - 1)]
    val high = present[(present.size * 0.99).toInt().coerceIn(0, present.size - 1)]

    return if (high - low < 1e-9) low to low + 1.0 else low to high
  }

  private fun fillBackground(image: BufferedImage) {
    val pixels = (image.raster.dataBuffer as DataBufferInt).data
    pixels.fill(NO_DATA)
  }

  private fun drawOverlays(
    image: BufferedImage,
    view: Viewport,
    options: RenderOptions,
    features: List<VectorFeature>
  ) {
    if (!options.chunkGrid && !options.cellGrid && options.seams.isEmpty() &&
      !(options.features && features.isNotEmpty())
    ) {
      return
    }

    val g = image.createGraphics()
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

      if (options.cellGrid) {
        drawGrid(g, view, config.baseResolution.metresPerCell, CELL_GRID_COLOR)
      }
      if (options.chunkGrid) {
        drawGrid(g, view, config.chunkExtent, CHUNK_GRID_COLOR)
      }
      if (options.features) {
        drawFeatures(g, view, options, features)
      }
      if (options.seams.isNotEmpty()) {
        drawSeams(g, view, options.seams)
      }
    } finally {
      g.dispose()
    }
  }

  /**
   * Grids that are switched on but too dense to draw at this zoom, named.
   *
   * Exists to answer "I ticked the box and nothing happened", which is a real and repeated confusion: the chunk
   * grid on a metre-voxel world needs about 5 m/px before it appears, so at any view wider than a street it is
   * silently absent. Reporting it is cheaper than explaining it.
   */
  fun gridsSuppressed(view: Viewport, options: RenderOptions): List<String> = buildList {
    if (options.cellGrid && isTooDense(config.baseResolution.metresPerCell, view)) add("raster")
    if (options.chunkGrid && isTooDense(config.chunkExtent, view)) add("chunk")
  }

  private fun isTooDense(spacing: Double, view: Viewport) =
    spacing / view.metresPerPixel < MIN_GRID_PIXELS

  /** Grid lines, skipped entirely when they would be denser than a few pixels apart. */
  private fun drawGrid(g: Graphics2D, view: Viewport, spacing: Double, color: Color) {
    if (spacing / view.metresPerPixel < MIN_GRID_PIXELS) return

    g.color = color
    g.stroke = BasicStroke(1f)

    val bounds = view.bounds
    var gx = floor(bounds.minX / spacing) * spacing
    while (gx <= bounds.maxX) {
      val sx = view.screenX(gx)
      g.draw(Line2D.Double(sx, 0.0, sx, view.heightPx.toDouble()))
      gx += spacing
    }

    var gy = floor(bounds.minY / spacing) * spacing
    while (gy <= bounds.maxY) {
      val sy = view.screenY(gy)
      g.draw(Line2D.Double(0.0, sy, view.widthPx.toDouble(), sy))
      gy += spacing
    }
  }

  private fun drawFeatures(
    g: Graphics2D,
    view: Viewport,
    options: RenderOptions,
    features: List<VectorFeature>
  ) {
    // Draw in (priority, id) order - the order they were stamped - so an overlapping pair reads the
    // way it was actually blended.
    for (feature in features) {
      // Filtered here rather than by the caller so that every path through the renderer - window, export,
      // town tool - honours the same set, and there is only one place for it to be got wrong.
      if (!options.draws(feature.kind)) continue

      if (feature is PointMarker) {
        drawMarker(g, view, feature)
        continue
      }

      val outlines = feature.outline()
      if (outlines.isEmpty()) {
        drawBounds(g, view, feature)
        continue
      }

      g.color = colorOf(feature.kind)
      g.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

      for (line in outlines) {
        val path = Path2D.Double()
        line.points.forEachIndexed { i, p ->
          val sx = view.screenX(p.x)
          val sy = view.screenY(p.y)
          if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
        }
        g.draw(path)
      }
    }
  }

  /**
   * A point marker as a filled dot, sized by how much it matters.
   *
   * These used to go through [drawBounds], and a point's bounding box is a point: `drawRect(x, y, 0, 0)` at
   * 38% alpha, one barely-visible pixel, **identical for a forty-thousand-person city and a hamlet of twenty**.
   * Settlements are the thing you most want to find on a world map and they were the least visible thing on it,
   * along with every gate, bridge, tomb and business in the world.
   *
   * The radius is in **screen space**, not world space, which is the whole point: a dot has to stay legible at
   * whole-world zoom, where a city's real extent is a fraction of a pixel, and must not swell into a blob when
   * you zoom to a street. Area therefore carries population rather than radius - `sqrt` - so a city reads as
   * bigger than a hamlet without a village becoming invisible next to it.
   */
  private fun drawMarker(g: Graphics2D, view: Viewport, marker: PointMarker) {
    val radius = radiusOf(marker)
    val cx = view.screenX(marker.position.x)
    val cy = view.screenY(marker.position.y)

    g.color = Color(colorOf(marker.kind).rgb)
    g.fill(Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2))

    // A thin dark ring, so a red dot on green forest and a red dot on brown steppe both read as a dot rather
    // than as a smudge the biome happened to make.
    g.color = MARKER_OUTLINE
    g.stroke = BasicStroke(1f)
    g.draw(Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2))
  }

  private fun radiusOf(marker: PointMarker): Double {
    if (marker.kind != FeatureKind.SETTLEMENT) return MINOR_MARKER_RADIUS

    val population = populationOf(marker)
      // No population channel: fall back to the tier, which is a coarse version of the same thing. A world
      // whose settlements carry neither still gets a visible dot rather than nothing.
      ?: return marker.optionalAttribute(SettlementChannels.TIER)
        ?.let { SETTLEMENT_MIN_RADIUS + (SettlementTier.entries.size - 1 - it).coerceAtLeast(0.0) }
        ?: SETTLEMENT_MIN_RADIUS

    val share = (population / CITY_POPULATION).coerceIn(0.0, 1.0)
    return SETTLEMENT_MIN_RADIUS + (SETTLEMENT_MAX_RADIUS - SETTLEMENT_MIN_RADIUS) * sqrt(share)
  }

  /** A feature with no outline still gets its influence bounds, so it is not invisible. */
  private fun drawBounds(g: Graphics2D, view: Viewport, feature: VectorFeature) {
    g.color = Color(colorOf(feature.kind).rgb and 0xFFFFFF or (0x60 shl 24), true)
    g.stroke = BasicStroke(1f)

    val x = view.screenX(feature.bbox.minX)
    val y = view.screenY(feature.bbox.maxY)
    g.drawRect(
      x.toInt(),
      y.toInt(),
      ceil(feature.bbox.width / view.metresPerPixel).toInt(),
      ceil(feature.bbox.height / view.metresPerPixel).toInt()
    )
  }

  private fun drawSeams(g: Graphics2D, view: Viewport, seams: List<ChunkSeamCheck.Seam>) {
    g.color = SEAM_COLOR
    g.stroke = BasicStroke(1.5f)

    for (seam in seams) {
      // Seam coordinates are voxel column indices; the column centre is half a voxel in.
      val worldX = (seam.worldColumnX + 0.5) * config.voxelSize
      val worldY = (seam.worldColumnY + 0.5) * config.voxelSize
      val sx = view.screenX(worldX)
      val sy = view.screenY(worldY)
      g.draw(Ellipse2D.Double(sx - 3.0, sy - 3.0, 6.0, 6.0))
    }
  }

  companion object {

    /** Where the field has no value. Deliberately not black: an empty stage must not look like sea. */
    val NO_DATA = Colors.rgb(28, 28, 34)

    /**
     * Below this many pixels apart a grid is not drawn at all.
     *
     * Worth knowing when a toggle appears to do nothing: on a world with kilometre cells the raster grid needs
     * about 167 m/px or finer, and a 32 m chunk grid needs 5.3 m/px - close to voxel scale. [gridsSuppressed]
     * reports it so the status bar can say so rather than leaving it to be inferred.
     */
    private const val MIN_GRID_PIXELS = 6.0

    private val CELL_GRID_COLOR = Color(255, 255, 255, 36)
    private val CHUNK_GRID_COLOR = Color(255, 220, 120, 90)
    private val SEAM_COLOR = Color(255, 40, 40)

    /** Enough contrast to read a dot on any biome colour without hiding the dot's own hue. */
    private val MARKER_OUTLINE = Color(20, 20, 26, 200)

    /** Screen-space dot radii, in pixels. A hamlet must stay visible; a city must not become a blob. */
    private const val SETTLEMENT_MIN_RADIUS = 2.5
    private const val SETTLEMENT_MAX_RADIUS = 9.0

    /** Everything that is not a settlement: gates, bridges, tombs, monuments, businesses. */
    private const val MINOR_MARKER_RADIUS = 2.0

    /** The population a dot is drawn at full size for - [SettlementTier.CITY]'s ceiling. */
    private const val CITY_POPULATION = 40_000.0

    /** Widest id span still treated as a set of categories worth naming. */
    private const val MAX_CATEGORIES = 4096

    /**
     * Deliberately an exhaustive `when` with no `else`: a stage that starts emitting a new kind of
     * feature must not be able to ship without the viewer being able to show it.
     */
    fun colorOf(kind: FeatureKind): Color = when (kind) {
      FeatureKind.FAULT -> Color(160, 90, 200)
      FeatureKind.ORE_DEPOSIT -> Color(255, 210, 70)
      FeatureKind.COASTLINE -> Color(250, 250, 250)
      FeatureKind.TECTONIC_BASIN -> Color(120, 170, 235)
      FeatureKind.GLACIAL_TROUGH, FeatureKind.FJORD -> Color(140, 220, 255)
      FeatureKind.CIRQUE -> Color(190, 235, 255)
      FeatureKind.RIVER_CHANNEL, FeatureKind.RIVER_CONFLUENCE -> Color(80, 160, 255)
      FeatureKind.ALLUVIAL_FAN, FeatureKind.DELTA -> Color(220, 200, 140)
      FeatureKind.MORAINE -> Color(190, 190, 120)
      FeatureKind.LAKE, FeatureKind.OXBOW_LAKE -> Color(60, 110, 220)
      FeatureKind.ROAD, FeatureKind.ROAD_JUNCTION -> Color(230, 170, 90)
      FeatureKind.BRIDGE -> Color(255, 130, 60)
      FeatureKind.SETTLEMENT_GRADING -> Color(255, 140, 140)
      FeatureKind.SETTLEMENT -> Color(255, 60, 60)
      FeatureKind.SETTLEMENT_HISTORY -> Color(190, 80, 80)
      FeatureKind.SETTLEMENT_ECONOMY -> Color(90, 200, 180)
      FeatureKind.RUIN -> Color(150, 130, 110)
      FeatureKind.BATTLEFIELD -> Color(150, 40, 40)
      FeatureKind.MONUMENT -> Color(240, 220, 150)
      FeatureKind.TOMB -> Color(170, 140, 200)
      FeatureKind.STREET -> Color(245, 215, 170)
      FeatureKind.TOWN_WALL -> Color(200, 200, 205)
      FeatureKind.GATE -> Color(255, 250, 230)
      FeatureKind.BUILDING -> Color(235, 225, 205)
      FeatureKind.BUSINESS -> Color(120, 220, 255)
      FeatureKind.ROADSIDE_INN -> Color(255, 195, 90)
    }
  }
}
