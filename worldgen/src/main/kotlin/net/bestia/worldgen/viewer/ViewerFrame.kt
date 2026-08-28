package net.bestia.worldgen.viewer

import net.bestia.worldgen.vector.FeatureKind
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.GridLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import net.bestia.worldgen.core.MovementMode
import net.bestia.worldgen.core.NavEdgeKind
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

/**
 * The offline viewer window: field list, render toggles, probe readout, seam check.
 *
 * The build order calls this the single highest-leverage investment in the whole pipeline, and the
 * reason is narrow: every stage from tectonics onward produces a field that is either right or
 * subtly wrong, and "subtly wrong" is invisible in a test assertion and obvious in a picture. The
 * probe panel exists for the other half of that - once you can see something is wrong, you need the
 * numbers under the cursor to find out why.
 */
class ViewerFrame(private val scene: WorldScene) : JFrame("worldgen - ${scene.name}") {

  private val canvas = WorldViewPanel(scene)
  private val status = JLabel(" ")
  private val probe = JPanel(GridLayout(0, 2, 8, 2))
  private val probeValues = LinkedHashMap<String, JLabel>()

  private var hillshade = true
  private var showFeatures = true
  private var chunkGrid = false
  private var cellGrid = false
  private var autoRange = false
  private var exaggeration = 2.0
  private var navGraph = false
  private var navWilderness = true
  private var regions = false
  private var regionLabels = true

  /**
   * Which feature kinds are drawn. Only ever holds kinds this world actually has.
   *
   * Starts as everything the world has except [RenderOptions.HIDDEN_BY_DEFAULT] - see there for why those five
   * are ink without information at map scale. Every one of them is one click away in the legend.
   */
  private val visibleKinds =
    scene.featureCensus.keys.toMutableSet().apply { removeAll(RenderOptions.HIDDEN_BY_DEFAULT) }

  init {
    defaultCloseOperation = DISPOSE_ON_CLOSE

    contentPane.layout = BorderLayout()
    contentPane.add(canvas, BorderLayout.CENTER)
    // Scrolled, because the side panel's height is driven by the number of fields - a full pipeline has
    // thirty-odd, and with a feature legend under them the column is taller than a 1080p screen.
    contentPane.add(
      JScrollPane(sidePanel()).apply {
        border = null
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        preferredSize = Dimension(SIDEBAR_WIDTH + 18, 0)
      },
      BorderLayout.EAST
    )
    contentPane.add(statusBar(), BorderLayout.SOUTH)

    canvas.onRendered = { map ->
      status.text = buildString {
        append(map.unavailable ?: "${map.field.name}: ${map.field.format(map.low)} .. ")
        if (map.unavailable == null) append(map.field.format(map.high))
        append("   |   ${"%.2f".format(Locale.ROOT, canvas.view.metresPerPixel)} m/px")
        // Worth stating outright rather than leaving to be inferred from the number: at voxel scale every
        // pixel is one materialised column, which is the only scale at which a single wrong block is visible.
        if (canvas.isVoxelScale()) append(" - 1 px = 1 voxel")

        // Otherwise a ticked grid that is too dense to draw looks like a broken toggle.
        val suppressed = canvas.suppressedGrids()
        if (suppressed.isNotEmpty()) append("   |   ${suppressed.joinToString("+")} grid too dense - zoom in")

        // The overlay caps itself on a large world; saying so beats leaving a partial graph to be mistaken
        // for the whole one. Same reasoning as the suppressed-grid note above.
        val nav = map.navStats
        if (nav.edgesInView > 0) {
          append("   |   nav: ${nav.edgesDrawn} edges")
          if (nav.capped) append(" of ${nav.edgesInView} in view (capped - zoom in for the rest)")
          append(", ${nav.nodesDrawn} nodes")
        }

        // Says so even at zero, unlike the nav line: a world that stopped before biomes has no partition
        // at all, and without this the toggle and a missing partition look identical.
        if (regions) {
          val place = map.regionStats
          append("   |   regions: ${place.regionsOnScreen} in view, ${place.labels} named")
        }

        append("   |   drag pan, wheel zoom, 1 voxel scale, F fit, H shade, C chunks, G cells, ")
        append("V features, A auto-range, N nav graph, M nav wilderness, R regions, S seam check, [ ] relief")
      }
    }

    canvas.onProbe = { worldX, worldY -> updateProbe(worldX, worldY) }

    canvas.addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) = handleKey(e)
    })

    applyOptions()
    pack()
    setLocationRelativeTo(null)
  }

  private fun sidePanel(): JPanel {
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
    panel.preferredSize = Dimension(SIDEBAR_WIDTH, 0)

    panel.add(heading("fields"))
    val fieldList = JList(scene.fields.map { it.name }.toTypedArray())
    fieldList.selectionMode = ListSelectionModel.SINGLE_SELECTION
    fieldList.selectedIndex = 0
    fieldList.addListSelectionListener { e ->
      if (!e.valueIsAdjusting && fieldList.selectedIndex >= 0) {
        canvas.activeField = scene.fields[fieldList.selectedIndex]
      }
    }
    panel.add(JScrollPane(fieldList).apply { preferredSize = Dimension(SIDEBAR_WIDTH - 20, 200) })

    panel.add(Box.createVerticalStrut(10))
    panel.add(heading("overlays"))
    panel.add(toggle("hillshade", hillshade) { hillshade = it; applyOptions() })
    panel.add(toggle("vector features", showFeatures) { showFeatures = it; applyOptions() })
    // Labelled with their spacing, because "chunk grid" and "raster cell grid" say nothing about which is
    // which, and both numbers are properties of this world rather than constants.
    panel.add(toggle("chunk grid (${metres(scene.config.chunkExtent)})", chunkGrid) {
      chunkGrid = it; applyOptions()
    })
    panel.add(toggle("world raster (${metres(scene.config.baseResolution.metresPerCell)})", cellGrid) {
      cellGrid = it; applyOptions()
    })
    panel.add(toggle("auto range", autoRange) { autoRange = it; applyOptions() })

    // Only offered when there is a graph to show, so a pipeline that stops before the navigation stage does
    // not present a toggle that can only ever do nothing.
    if (scene.navGraph.nodes.isNotEmpty()) {
      panel.add(
        toggle("nav graph (${scene.navGraph.nodes.size} nodes, ${scene.navGraph.edges.size} edges)", navGraph) {
          navGraph = it; applyOptions()
        }
      )
      panel.add(toggle("   ...including wilderness", navWilderness) { navWilderness = it; applyOptions() })
    }

    // Unguarded, unlike the nav graph above: the partition exists on any world that got as far as biomes,
    // and the toggle is how you find out that it did not.
    panel.add(toggle("place regions (R)", regions) { regions = it; applyOptions() })
    panel.add(toggle("   ...with names", regionLabels) { regionLabels = it; applyOptions() })

    panel.add(Box.createVerticalStrut(10))
    panel.add(featureLegend())

    if (scene.navGraph.nodes.isNotEmpty()) {
      panel.add(Box.createVerticalStrut(10))
      panel.add(navLegend())
    }

    panel.add(Box.createVerticalStrut(10))
    panel.add(heading("under cursor"))
    for (f in scene.fields) {
      val value = JLabel("-")
      value.font = Font(Font.MONOSPACED, Font.PLAIN, 11)
      probeValues[f.name] = value
      probe.add(JLabel(f.name).apply { font = Font(Font.SANS_SERIF, Font.PLAIN, 11) })
      probe.add(value)
    }
    probe.alignmentX = LEFT_ALIGNMENT
    panel.add(probe)

    panel.add(Box.createVerticalGlue())

    return panel
  }

  private fun statusBar(): JPanel {
    val bar = JPanel(BorderLayout())
    bar.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
    status.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
    status.foreground = Color(70, 70, 76)
    bar.add(status, BorderLayout.WEST)
    return bar
  }

  /**
   * The vector overlay's key: which colour is which kind, how many there are, and which are drawn.
   *
   * Both halves of the problem in one control. The map drew coloured lines with nothing anywhere saying what
   * they were, and the overlay was a single switch, so the only way to stop several thousand sub-pixel
   * buildings from burying the rivers was to turn the rivers off too.
   *
   * Only the kinds this world has get a row, so the list is the world's own inventory rather than the
   * generator's vocabulary. Note that five colours are shared by two kinds each - river channel with
   * confluence, road with junction, lake with oxbow, trough with fjord, fan with delta - which is deliberate:
   * they are one visual class and telling them apart on a map was never the point.
   */
  private fun featureLegend(): JPanel {
    val section = JPanel()
    section.layout = BoxLayout(section, BoxLayout.Y_AXIS)
    section.alignmentX = LEFT_ALIGNMENT
    section.add(heading("features"))

    if (scene.featureCensus.isEmpty()) {
      section.add(JLabel("none in this world").apply {
        font = Font(Font.SANS_SERIF, Font.ITALIC, 11)
        alignmentX = LEFT_ALIGNMENT
      })
      return section
    }

    val rows = JPanel()
    rows.layout = BoxLayout(rows, BoxLayout.Y_AXIS)
    for ((kind, count) in scene.featureCensus) {
      rows.add(featureRow(kind, count))
    }

    section.add(
      JScrollPane(rows).apply {
        preferredSize = Dimension(SIDEBAR_WIDTH - 20, LEGEND_HEIGHT)
        maximumSize = Dimension(SIDEBAR_WIDTH - 20, LEGEND_HEIGHT)
        alignmentX = LEFT_ALIGNMENT
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
      }
    )
    return section
  }

  /**
   * The navigation overlay's key.
   *
   * Read-only, unlike the feature legend: the graph's kinds are not independently switchable, because the two
   * useful ways to cut it down - roads only, or everything - are already the `nav wilderness` toggle, and a
   * per-kind checkbox for five kinds of which one is 95% of the data would suggest a control that is not there.
   *
   * The two mode colours are listed beside the kinds because they answer a different question about the same
   * edge: a blue hop is one that fords water, which is exactly the edge a creature that cannot swim is refused,
   * and an amber one is steep enough to have been tagged as a climb.
   */
  private fun navLegend(): JPanel {
    val section = JPanel()
    section.layout = BoxLayout(section, BoxLayout.Y_AXIS)
    section.alignmentX = LEFT_ALIGNMENT
    section.add(heading("nav graph"))

    for ((kind, count) in scene.navOverlay.edgeCensus) {
      section.add(
        navRow(
          NavGraphOverlay.colorOf(kind, emptySet()),
          "${kind.name.lowercase()}  ($count)"
        )
      )
    }

    val wilderness = scene.navGraph.edges.count { it.kind == NavEdgeKind.WILDERNESS }
    if (wilderness > 0) {
      val fords = scene.navGraph.edges.count { MovementMode.SWIM in it.modes }
      val climbs = scene.navGraph.edges.count { MovementMode.CLIMB in it.modes }
      section.add(
        navRow(
          NavGraphOverlay.colorOf(NavEdgeKind.WILDERNESS, setOf(MovementMode.SWIM)),
          "...that ford water  ($fords)"
        )
      )
      section.add(
        navRow(
          NavGraphOverlay.colorOf(NavEdgeKind.WILDERNESS, setOf(MovementMode.CLIMB)),
          "...that climb  ($climbs)"
        )
      )
    }

    return section
  }

  private fun navRow(color: Color, text: String): JPanel {
    val row = JPanel()
    row.layout = BoxLayout(row, BoxLayout.X_AXIS)
    row.alignmentX = LEFT_ALIGNMENT
    row.add(JLabel(text, Swatch(color), JLabel.LEADING).apply {
      font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
      iconTextGap = 6
    })
    row.add(Box.createHorizontalGlue())
    return row
  }

  private fun featureRow(kind: FeatureKind, count: Int): JPanel {
    val row = JPanel()
    row.layout = BoxLayout(row, BoxLayout.X_AXIS)
    row.alignmentX = LEFT_ALIGNMENT

    // The check box keeps its own indicator, so the colour goes in a swatch beside it rather than as the
    // box's icon - setting `icon` on a JCheckBox replaces the tick and leaves no way to see the state.
    row.add(
      JCheckBox("", kind in visibleKinds).apply {
        isFocusable = false
        isOpaque = false
        addActionListener {
          if (isSelected) visibleKinds.add(kind) else visibleKinds.remove(kind)
          applyOptions()
        }
      }
    )
    row.add(JLabel(Swatch(MapRenderer.colorOf(kind))))
    row.add(Box.createHorizontalStrut(6))
    row.add(
      JLabel("${kind.name.lowercase().replace('_', ' ')}  ${"%,d".format(Locale.ROOT, count)}").apply {
        font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
      }
    )
    row.add(Box.createHorizontalGlue())
    return row
  }

  private fun heading(text: String) = JLabel(text).apply {
    font = Font(Font.SANS_SERIF, Font.BOLD, 11)
    alignmentX = LEFT_ALIGNMENT
  }

  /** A length as the shortest thing that reads: `32 m`, `1 km`, `4 km`. */
  private fun metres(value: Double): String = when {
    value >= 1_000.0 && value % 1_000.0 == 0.0 -> "${(value / 1_000.0).toInt()} km"
    value >= 1_000.0 -> "${"%.1f".format(Locale.ROOT, value / 1_000.0)} km"
    else -> "${value.toInt()} m"
  }

  private fun toggle(text: String, initial: Boolean, onChange: (Boolean) -> Unit) =
    JCheckBox(text, initial).apply {
      font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
      alignmentX = LEFT_ALIGNMENT
      isFocusable = false
      addActionListener { onChange(isSelected) }
    }

  private fun handleKey(e: KeyEvent) {
    when (e.keyCode) {
      KeyEvent.VK_F -> canvas.fitWorld()
      KeyEvent.VK_1, KeyEvent.VK_NUMPAD1 -> canvas.zoomToVoxelScale()
      KeyEvent.VK_H -> { hillshade = !hillshade; applyOptions() }
      KeyEvent.VK_V -> { showFeatures = !showFeatures; applyOptions() }
      KeyEvent.VK_C -> { chunkGrid = !chunkGrid; applyOptions() }
      KeyEvent.VK_G -> { cellGrid = !cellGrid; applyOptions() }
      KeyEvent.VK_A -> { autoRange = !autoRange; applyOptions() }
      KeyEvent.VK_N -> { navGraph = !navGraph; applyOptions() }
      KeyEvent.VK_M -> { navWilderness = !navWilderness; applyOptions() }
      KeyEvent.VK_R -> { regions = !regions; applyOptions() }
      KeyEvent.VK_OPEN_BRACKET -> { exaggeration = (exaggeration / 1.4).coerceAtLeast(0.2); applyOptions() }
      KeyEvent.VK_CLOSE_BRACKET -> { exaggeration = (exaggeration * 1.4).coerceAtMost(30.0); applyOptions() }
      KeyEvent.VK_S -> runSeamCheck()
      KeyEvent.VK_ESCAPE -> canvas.clearSeams()
      KeyEvent.VK_PLUS, KeyEvent.VK_EQUALS -> canvas.zoomAt(canvas.width / 2, canvas.height / 2, 1.4)
      KeyEvent.VK_MINUS -> canvas.zoomAt(canvas.width / 2, canvas.height / 2, 1 / 1.4)
    }
  }

  private fun runSeamCheck() {
    status.text = "running chunk boundary check..."

    // Off the UI thread: it generates the visible block of chunks several times over.
    Thread {
      val report = canvas.runSeamCheck()
      SwingUtilities.invokeLater {
        status.text = when {
          report == null -> "this scene has no chunk pipeline to check"
          report.isClean -> "seam check clean - ${report.columnsCompared} shared columns agree"
          else -> "SEAMS: ${report.seams.size}/${report.columnsCompared} columns disagree, " +
              "worst ${"%.4f".format(Locale.ROOT, report.worstDelta)} m - marked in red"
        }
      }
    }.apply { isDaemon = true }.start()
  }

  private fun applyOptions() {
    canvas.options = RenderOptions(
      hillshade = hillshade,
      exaggeration = exaggeration,
      features = showFeatures,
      // A copy, not the live set: RenderOptions crosses to the render thread, and handing it a set the UI
      // thread is still mutating is a data race that would show up as an occasional wrong overlay.
      featureKinds = visibleKinds.toSet(),
      chunkGrid = chunkGrid,
      cellGrid = cellGrid,
      autoRange = autoRange,
      navGraph = navGraph,
      navWilderness = navWilderness,
      regions = regions,
      regionLabels = regionLabels
    )
  }

  private fun updateProbe(worldX: Double, worldY: Double) {
    for ((name, value) in canvas.probeAll(worldX, worldY)) {
      probeValues[name]?.text = value
    }
    status.toolTipText = "world (${"%.1f".format(Locale.ROOT, worldX)}, ${"%.1f".format(Locale.ROOT, worldY)}) m"
  }

  /** A colour chip for the legend. The one hand-painted component in the side panel. */
  private class Swatch(private val color: Color) : Icon {

    override fun getIconWidth() = 11

    override fun getIconHeight() = 11

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
      g.color = color
      g.fillRect(x, y + 1, 11, 9)
      // An outline, so a near-white kind - coastline, gate - is still a chip rather than a hole.
      g.color = Color(0, 0, 0, 90)
      g.drawRect(x, y + 1, 10, 8)
    }
  }

  companion object {

    private const val SIDEBAR_WIDTH = 300

    /** Tall enough for a dozen kinds; the rest scroll. A full pipeline emits about twenty. */
    private const val LEGEND_HEIGHT = 190

    fun open(scene: WorldScene) {
      SwingUtilities.invokeLater {
        val frame = ViewerFrame(scene)
        frame.isVisible = true
        frame.canvas.requestFocusInWindow()
      }
    }
  }
}
