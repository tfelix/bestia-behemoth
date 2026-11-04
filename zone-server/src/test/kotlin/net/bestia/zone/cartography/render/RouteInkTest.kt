package net.bestia.zone.cartography.render

import net.bestia.worldgen.civ.RoadClass
import net.bestia.worldgen.render.Viewport
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.LinearFeatures
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.world.GeneratedWorlds
import net.bestia.zone.world.ReadsLargeWorlds
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.test.assertTrue

/**
 * Each road class is inked in its own style.
 *
 * The guard on the bug that made this file read the class rather than guess it: the guess went stale with the
 * road widths and every road came out in one style, which no seam test or golden image would have caught -
 * the map simply had one kind of road on it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ReadsLargeWorlds
class RouteInkTest {

  private val world: TileInputs by lazy { TileInputs.of(GeneratedWorlds.of(GeneratedWorlds.SEEDS.first())) }

  @Test
  fun `a cobbled road is a solid double line, a dirt road dashed, and a trail dotted`() {
    val cobbled = render(RoadClass.COBBLED)
    val dirt = render(RoadClass.DIRT_ROAD)
    val trail = render(RoadClass.TRAIL)

    // How much of the road's length is inked at all: solid, then dashes, then dots.
    assertTrue(cobbled.inkedAlong > 0.95, "the cobbled road is broken: ${cobbled.inkedAlong}")
    assertTrue(dirt.inkedAlong in 0.4..0.85, "the dirt road is not dashed: ${dirt.inkedAlong}")
    assertTrue(trail.inkedAlong < dirt.inkedAlong, "the trail is no more broken than the dirt road: ${trail.inkedAlong}")

    // The cobbled road is the only one with a casing: ink either side of a paler middle.
    assertTrue(cobbled.rowsAcross >= 3, "the cobbled road is a single line ${cobbled.rowsAcross} px wide")
    assertTrue(cobbled.paleMiddle, "the cobbled road has no paper down its middle")
    assertTrue(!dirt.paleMiddle && !trail.paleMiddle, "an earth road was drawn as a double line")
  }

  private class Inked(val inkedAlong: Double, val rowsAcross: Int, val paleMiddle: Boolean)

  private fun render(roadClass: RoadClass): Inked {
    val view = Viewport(centerX = 0.0, centerY = 0.0, metresPerPixel = METRES_PER_PIXEL, widthPx = WIDTH, heightPx = HEIGHT)
    val road = LinearFeatures.road(
      id = FeatureId(1L),
      centerline = Polyline(listOf(Vec2d(view.minX - 50.0, 0.0), Vec2d(view.minX + WIDTH * METRES_PER_PIXEL + 50.0, 0.0))),
      stationSpacing = 20.0,
      surfaceElevation = { 100.0 },
      halfWidth = { roadClass.halfWidth },
      roadClass = roadClass.ordinal
    )
    val inputs = world.let {
      TileInputs(
        config = it.config,
        elevation = it.elevation,
        waterLevel = it.waterLevel,
        biome = it.biome,
        canopyCover = it.canopyCover,
        iceThickness = it.iceThickness,
        discharge = it.discharge,
        chronicle = it.chronicle,
        baseHeight = it.baseHeight,
        featuresIn = { listOf(road) }
      )
    }

    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    RouteInk.draw(g, view, inputs, AtlasPalette.PARCHMENT)
    g.dispose()

    val middle = HEIGHT / 2
    fun alpha(x: Int, y: Int) = image.getRGB(x, y) ushr 24

    // A column counts as inked if any pixel within two of the line is.
    val inked = (MARGIN until WIDTH - MARGIN).count { x -> (middle - 2..middle + 2).any { alpha(x, it) > 40 } }
    val rows = (0 until HEIGHT).count { y -> (MARGIN until WIDTH - MARGIN).any { alpha(it, y) > 40 } }

    // A double line is darker either side than in the middle, sampled across a column that is inked.
    val column = (MARGIN until WIDTH - MARGIN).first { x -> alpha(x, middle) > 40 || alpha(x, middle - 1) > 40 }
    val across = (middle - 3..middle + 3).map { image.getRGB(column, it) }.filter { it ushr 24 > 40 }
    val lightness = across.map { ((it shr 16) and 0xff) + ((it shr 8) and 0xff) + (it and 0xff) }
    val pale = lightness.size >= 3 && lightness.drop(1).dropLast(1).max() > lightness.first() + 60 &&
        lightness.drop(1).dropLast(1).max() > lightness.last() + 60

    return Inked(inked.toDouble() / (WIDTH - 2 * MARGIN), rows, pale)
  }

  private companion object {
    const val WIDTH = 240
    const val HEIGHT = 24
    const val MARGIN = 10

    /** Close enough that a road is drawn at all (`MapVisibility`), far enough that the dashes repeat. */
    const val METRES_PER_PIXEL = 8.0
  }
}
