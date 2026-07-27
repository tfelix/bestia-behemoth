package net.bestia.worldgen.viewer

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
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

  init {
    defaultCloseOperation = DISPOSE_ON_CLOSE

    contentPane.layout = BorderLayout()
    contentPane.add(canvas, BorderLayout.CENTER)
    contentPane.add(sidePanel(), BorderLayout.EAST)
    contentPane.add(statusBar(), BorderLayout.SOUTH)

    canvas.onRendered = { map ->
      status.text = buildString {
        append(map.unavailable ?: "${map.field.name}: ${map.field.format(map.low)} .. ")
        if (map.unavailable == null) append(map.field.format(map.high))
        append("   |   ${"%.2f".format(canvas.view.metresPerPixel)} m/px")
        append("   |   drag pan, wheel zoom, F fit, H shade, C chunks, G cells, V features, ")
        append("A auto-range, S seam check, [ ] relief")
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
    panel.preferredSize = Dimension(260, 0)

    panel.add(heading("fields"))
    val fieldList = JList(scene.fields.map { it.name }.toTypedArray())
    fieldList.selectionMode = ListSelectionModel.SINGLE_SELECTION
    fieldList.selectedIndex = 0
    fieldList.addListSelectionListener { e ->
      if (!e.valueIsAdjusting && fieldList.selectedIndex >= 0) {
        canvas.activeField = scene.fields[fieldList.selectedIndex]
      }
    }
    panel.add(JScrollPane(fieldList).apply { preferredSize = Dimension(240, 200) })

    panel.add(Box.createVerticalStrut(10))
    panel.add(heading("overlays"))
    panel.add(toggle("hillshade", hillshade) { hillshade = it; applyOptions() })
    panel.add(toggle("vector features", showFeatures) { showFeatures = it; applyOptions() })
    panel.add(toggle("chunk grid", chunkGrid) { chunkGrid = it; applyOptions() })
    panel.add(toggle("raster cell grid", cellGrid) { cellGrid = it; applyOptions() })
    panel.add(toggle("auto range", autoRange) { autoRange = it; applyOptions() })

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

  private fun heading(text: String) = JLabel(text).apply {
    font = Font(Font.SANS_SERIF, Font.BOLD, 11)
    alignmentX = LEFT_ALIGNMENT
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
      KeyEvent.VK_H -> { hillshade = !hillshade; applyOptions() }
      KeyEvent.VK_V -> { showFeatures = !showFeatures; applyOptions() }
      KeyEvent.VK_C -> { chunkGrid = !chunkGrid; applyOptions() }
      KeyEvent.VK_G -> { cellGrid = !cellGrid; applyOptions() }
      KeyEvent.VK_A -> { autoRange = !autoRange; applyOptions() }
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
              "worst ${"%.4f".format(report.worstDelta)} m - marked in red"
        }
      }
    }.apply { isDaemon = true }.start()
  }

  private fun applyOptions() {
    canvas.options = RenderOptions(
      hillshade = hillshade,
      exaggeration = exaggeration,
      features = showFeatures,
      chunkGrid = chunkGrid,
      cellGrid = cellGrid,
      autoRange = autoRange
    )
  }

  private fun updateProbe(worldX: Double, worldY: Double) {
    for ((name, value) in canvas.probeAll(worldX, worldY)) {
      probeValues[name]?.text = value
    }
    status.toolTipText = "world (${"%.1f".format(worldX)}, ${"%.1f".format(worldY)}) m"
  }

  companion object {

    fun open(scene: WorldScene) {
      SwingUtilities.invokeLater {
        val frame = ViewerFrame(scene)
        frame.isVisible = true
        frame.canvas.requestFocusInWindow()
      }
    }
  }
}
