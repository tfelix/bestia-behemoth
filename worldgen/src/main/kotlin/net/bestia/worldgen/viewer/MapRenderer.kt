package net.bestia.worldgen.viewer

import net.bestia.worldgen.core.ChunkSeamCheck
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.vector.FeatureKind
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

/** What to draw on top of the field, and how. */
data class RenderOptions(
  val hillshade: Boolean = true,
  val exaggeration: Double = 2.0,
  val features: Boolean = true,
  val chunkGrid: Boolean = false,
  val cellGrid: Boolean = false,
  /** Stretch the palette to the values actually on screen, ignoring its declared range. */
  val autoRange: Boolean = false,
  /** Draw markers where [ChunkSeamCheck] found disagreeing columns. */
  val seams: List<ChunkSeamCheck.Seam> = emptyList()
)

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
  val unavailable: String? = null
)

/**
 * Renders a [ScalarField] through a [Viewport], with hillshading and vector overlays.
 *
 * Sampling is one field evaluation per pixel at the pixel's world centre - no smoothing, no
 * mipmapping. That is deliberate: this tool exists to show what the pipeline produces, and a
 * renderer that quietly filters the data is a renderer that hides the bug you opened it to find.
 */
class MapRenderer(private val config: WorldConfig) {

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

    return RenderedMap(image, field, range.first, range.second)
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
        drawFeatures(g, view, features)
      }
      if (options.seams.isNotEmpty()) {
        drawSeams(g, view, options.seams)
      }
    } finally {
      g.dispose()
    }
  }

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

  private fun drawFeatures(g: Graphics2D, view: Viewport, features: List<VectorFeature>) {
    // Draw in (priority, id) order - the order they were stamped - so an overlapping pair reads the
    // way it was actually blended.
    for (feature in features) {
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

    private const val MIN_GRID_PIXELS = 6.0

    private val CELL_GRID_COLOR = Color(255, 255, 255, 36)
    private val CHUNK_GRID_COLOR = Color(255, 220, 120, 90)
    private val SEAM_COLOR = Color(255, 40, 40)

    /**
     * Deliberately an exhaustive `when` with no `else`: a stage that starts emitting a new kind of
     * feature must not be able to ship without the viewer being able to show it.
     */
    fun colorOf(kind: FeatureKind): Color = when (kind) {
      FeatureKind.FAULT -> Color(160, 90, 200)
      FeatureKind.ORE_DEPOSIT -> Color(255, 210, 70)
      FeatureKind.COASTLINE -> Color(250, 250, 250)
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
    }
  }
}
