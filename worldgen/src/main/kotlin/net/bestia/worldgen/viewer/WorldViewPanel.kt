package net.bestia.worldgen.viewer

import net.bestia.worldgen.core.ChunkSeamCheck
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * The map canvas: pan, zoom, probe readout, and a legend.
 *
 * Rendering happens on a background thread and the result is swapped in when it arrives. The chunk
 * view can take a second or two on a cold cache - it is generating actual chunks - and a debug tool
 * that freezes while you drag it is one you stop reaching for.
 */
class WorldViewPanel(private var scene: WorldScene) : JPanel() {

  /** Not named `field`: that is the backing-field keyword, and the shadowing reads terribly. */
  var activeField: ScalarField = scene.fields.first()
    set(value) {
      field = value
      invalidateRender()
    }

  var options: RenderOptions = RenderOptions()
    set(value) {
      field = value
      invalidateRender()
    }

  var view: Viewport = Viewport(0.0, 0.0, 1.0, 1, 1)
    private set

  /** Called after every completed render, so the frame can refresh its status bar. */
  var onRendered: (RenderedMap) -> Unit = {}

  /** Called as the pointer moves, with the world position under it. */
  var onProbe: (Double, Double) -> Unit = { _, _ -> }

  private var renderer = MapRenderer(scene.config)
  private var rendered: RenderedMap? = null
  private var seams: List<ChunkSeamCheck.Seam> = emptyList()

  private val renderThread = Executors.newSingleThreadExecutor { r ->
    Thread(r, "worldgen-viewer-render").apply { isDaemon = true }
  }
  private val requestCounter = AtomicLong()

  private var dragFrom: MouseEvent? = null
  private var fitted = false

  /** Where the pointer last was, so the voxel-scale snap zooms into what you are looking at. */
  private var cursorX = -1
  private var cursorY = -1

  init {
    background = Color(MapRenderer.NO_DATA)
    preferredSize = Dimension(1100, 760)
    isFocusable = true

    val mouse = object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        dragFrom = e
        rememberCursor(e)
        requestFocusInWindow()
      }

      override fun mouseReleased(e: MouseEvent) {
        dragFrom = null
      }

      override fun mouseDragged(e: MouseEvent) {
        val from = dragFrom ?: return
        view = view.pannedByPixels(e.x - from.x, e.y - from.y)
        dragFrom = e
        rememberCursor(e)
        invalidateRender()
      }

      override fun mouseMoved(e: MouseEvent) {
        rememberCursor(e)
        onProbe(view.worldX(e.x), view.worldY(e.y))
      }

      override fun mouseWheelMoved(e: MouseWheelEvent) {
        rememberCursor(e)
        val factor = if (e.wheelRotation < 0) ZOOM_STEP else 1.0 / ZOOM_STEP
        zoomAt(e.x, e.y, factor)
      }
    }

    addMouseListener(mouse)
    addMouseMotionListener(mouse)
    addMouseWheelListener(mouse)
  }

  fun show(scene: WorldScene) {
    this.scene = scene
    this.renderer = MapRenderer(scene.config)
    this.activeField = scene.fields.first()
    this.seams = emptyList()
    fitWorld()
  }

  fun fitWorld() {
    if (width > 0 && height > 0) {
      view = Viewport.fit(scene.bounds, width, height)
      fitted = true
      invalidateRender()
    }
  }

  fun zoomAt(px: Int, py: Int, factor: Double) {
    val zoomed = view.zoomedAt(px, py, factor)
    view = zoomed.copy(
      metresPerPixel = Viewport.clampScale(zoomed.metresPerPixel, scene.bounds)
    )
    invalidateRender()
  }

  /**
   * Snaps to exactly one pixel per voxel, anchored under the cursor.
   *
   * The scale at which the voxel views stop being a summary: every pixel is one column the materialiser
   * actually produced, so a single wrong block is a single wrong pixel rather than something averaged
   * away. Reaching it by wheel notches is luck - 1.25 to the power of anything is not 1 - and being a
   * few percent off voxel scale makes even a perfect surface look like it has uneven columns.
   */
  fun zoomToVoxelScale() {
    view = view.scaledAt(
      px = if (cursorX in 0 until width) cursorX else width / 2,
      py = if (cursorY in 0 until height) cursorY else height / 2,
      metresPerPixel = Viewport.clampScale(scene.config.voxelSize, scene.bounds)
    )
    invalidateRender()
  }

  /** True when the current view is at voxel scale - the status bar says so, so it is not guesswork. */
  fun isVoxelScale() = view.metresPerPixel == scene.config.voxelSize

  /** Runs the boundary check over what is on screen and marks every disagreeing column. */
  fun runSeamCheck(): ChunkSeamCheck.Report? {
    val report = scene.seamCheck(view)
    seams = report?.seams ?: emptyList()
    invalidateRender()
    return report
  }

  fun clearSeams() {
    seams = emptyList()
    invalidateRender()
  }

  fun valueUnderCursor(worldX: Double, worldY: Double) = activeField.valueAt(worldX, worldY)

  /** Every field's value at one position - the readout that makes cross-layer checks possible. */
  fun probeAll(worldX: Double, worldY: Double): List<Pair<String, String>> =
    scene.fields.map { f ->
      val value = if (f.availabilityFor(view) == null) f.valueAt(worldX, worldY) else Double.NaN
      f.name to "${f.format(value)} ${f.unit}".trim()
    }

  private fun rememberCursor(e: MouseEvent) {
    cursorX = e.x
    cursorY = e.y
  }

  private fun invalidateRender() {
    val request = requestCounter.incrementAndGet()
    val snapshot = view
    val currentField = activeField
    val currentOptions = options.copy(seams = seams)
    val features = if (currentOptions.features) scene.featuresIn(snapshot.bounds) else emptyList()

    renderThread.execute {
      // A drag produces requests faster than they can be served; only the newest one is worth
      // drawing, and skipping the stale ones is what keeps panning responsive.
      if (requestCounter.get() != request) return@execute

      val result = try {
        renderer.render(currentField, snapshot, currentOptions, features)
      } catch (e: Exception) {
        // A stage that throws must not take the window with it - show it in the status bar.
        RenderedMap(
          BufferedImage(snapshot.widthPx, snapshot.heightPx, BufferedImage.TYPE_INT_RGB),
          currentField, 0.0, 0.0, "render failed: ${e.message}"
        )
      }

      SwingUtilities.invokeLater {
        if (requestCounter.get() == request) {
          rendered = result
          onRendered(result)
          repaint()
        }
      }
    }
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    if (!fitted || view.widthPx != width || view.heightPx != height) {
      if (width > 0 && height > 0) {
        view = if (fitted) view.resized(width, height) else Viewport.fit(scene.bounds, width, height)
        fitted = true
        invalidateRender()
      }
    }

    val g2 = g as Graphics2D
    val current = rendered

    if (current == null) {
      drawMessage(g2, "rendering...")
      return
    }

    g2.drawImage(current.image, 0, 0, null)

    current.unavailable?.let {
      drawMessage(g2, it)
      return
    }

    drawLegend(g2, current)
  }

  private fun drawMessage(g2: Graphics2D, message: String) {
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g2.color = Color(230, 230, 235)
    g2.font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
    g2.drawString(message, 16, 26)
  }

  /** A colour bar with the numbers on it. A map without one is a picture, not a measurement. */
  private fun drawLegend(g2: Graphics2D, map: RenderedMap) {
    val barWidth = 220
    val barHeight = 12
    val x = 16
    val y = height - 44

    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    g2.color = Color(0, 0, 0, 150)
    g2.fillRoundRect(x - 8, y - 22, barWidth + 16, barHeight + 42, 8, 8)

    // A composite has no single colour scale, so there is no bar that would be true - its blues mean depth
    // and its greens mean vegetation. The range labels below still apply: they are the surface height, which
    // is what the field's own values are, and the sidebar readout names what is under the cursor.
    if (map.field !is CompositeField) {
      val palette = map.field.palette.withRange(map.low, map.high)
      for (i in 0 until barWidth) {
        g2.color = Color(palette.rgb(map.low + (map.high - map.low) * i / (barWidth - 1.0)))
        g2.fillRect(x + i, y, 1, barHeight)
      }
    }

    g2.color = Color(235, 235, 240)
    g2.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
    g2.drawString("${map.field.name} ${map.field.unit}".trim(), x, y - 8)
    g2.drawString(map.field.format(map.low), x, y + barHeight + 14)

    val highLabel = map.field.format(map.high)
    g2.drawString(highLabel, x + barWidth - g2.fontMetrics.stringWidth(highLabel), y + barHeight + 14)
  }

  private companion object {
    const val ZOOM_STEP = 1.25
  }
}
