package net.bestia.worldgen.viewer

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.render.Colors
import net.bestia.worldgen.render.GradientRamp
import net.bestia.worldgen.render.Hillshade
import net.bestia.worldgen.render.Ramps
import net.bestia.worldgen.render.Viewport
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenderTest {

  private val config = WorldConfig(seed = 1L, widthCells = 8, heightCells = 8)
  private val renderer = MapRenderer(config)
  private val view = Viewport(0.0, 0.0, 10.0, 40, 30)

  /** A field defined by a lambda, so a test can render exactly the values it cares about. */
  private class TestField(
    override val name: String,
    override val palette: Palette,
    private val value: (Double, Double) -> Double
  ) : ScalarField {
    override fun valueAt(worldX: Double, worldY: Double) = value(worldX, worldY)
  }

  private fun pixel(map: RenderedMap, px: Int, py: Int) = map.image.getRGB(px, py) and 0xFFFFFF

  @Test
  fun `a gradient ramp hits its endpoints and clamps outside them`() {
    val ramp = GradientRamp.of(0.0 to Colors.rgb(0, 0, 0), 1.0 to Colors.rgb(255, 255, 255))

    assertEquals(Colors.rgb(0, 0, 0), ramp.rgb(0.0))
    assertEquals(Colors.rgb(255, 255, 255), ramp.rgb(1.0))
    assertEquals(Colors.rgb(0, 0, 0), ramp.rgb(-4.0))
    assertEquals(Colors.rgb(255, 255, 255), ramp.rgb(17.0))
    assertTrue(Colors.red(ramp.rgb(0.5)) in 120..135, "midpoint should be mid grey")
  }

  @Test
  fun `a gradient ramp is monotonic between stops`() {
    var previous = -1
    var t = 0.0
    while (t <= 1.0) {
      val red = Colors.red(Ramps.GRAYSCALE.rgb(t))
      assertTrue(red >= previous, "grayscale went backwards at $t")
      previous = red
      t += 0.01
    }
  }

  @Test
  fun `elevation is coloured on the correct side of sea level`() {
    // The break at sea level is the whole reason this palette exists: a coastline that is a few
    // metres wrong has to look wrong, not merely a slightly different shade of green.
    val palette = ElevationPalette(seaLevel = 0.0)

    val justBelow = palette.rgb(-0.5)
    val justAbove = palette.rgb(0.5)

    assertTrue(Colors.blue(justBelow) > Colors.green(justBelow), "below sea level should read as water")
    assertTrue(Colors.green(justAbove) > Colors.blue(justAbove), "above sea level should read as land")
  }

  @Test
  fun `moving the range of an elevation palette leaves sea level where it is`() {
    val stretched = ElevationPalette(seaLevel = 120.0).withRange(-40.0, 900.0)

    assertTrue(Colors.blue(stretched.rgb(119.0)) > Colors.green(stretched.rgb(119.0)))
    assertTrue(Colors.green(stretched.rgb(121.0)) > Colors.blue(stretched.rgb(121.0)))
  }

  @Test
  fun `a constant field renders as one colour and the image has the requested size`() {
    val field = TestField("flat", ContinuousPalette(Ramps.VIRIDIS, 0.0..100.0)) { _, _ -> 50.0 }

    val map = renderer.render(field, view, RenderOptions(hillshade = false))

    assertEquals(40, map.image.width)
    assertEquals(30, map.image.height)
    assertNull(map.unavailable)

    val expected = pixel(map, 0, 0)
    for (py in 0 until 30) {
      for (px in 0 until 40) {
        assertEquals(expected, pixel(map, px, py), "pixel ($px,$py)")
      }
    }
  }

  @Test
  fun `columns with no value are drawn as no-data rather than as a valid colour`() {
    // A stage that wrote a smaller region than it claimed must look like a hole, not like sea.
    val field = TestField("holed", ContinuousPalette(Ramps.VIRIDIS, 0.0..1.0)) { x, _ ->
      if (x < 0.0) Double.NaN else 1.0
    }

    val map = renderer.render(field, view, RenderOptions(hillshade = false))

    assertEquals(MapRenderer.NO_DATA, pixel(map, 0, 15))
    assertNotEquals(MapRenderer.NO_DATA, pixel(map, 39, 15))
  }

  @Test
  fun `auto range stretches the palette over the values actually present`() {
    val field = TestField("narrow", ContinuousPalette(Ramps.VIRIDIS, 0.0..1000.0)) { x, _ ->
      400.0 + x / 1000.0
    }

    val declared = renderer.render(field, view, RenderOptions(hillshade = false))
    val auto = renderer.render(field, view, RenderOptions(hillshade = false, autoRange = true))

    assertEquals(0.0, declared.low, 1e-9)
    assertEquals(1000.0, declared.high, 1e-9)
    assertTrue(auto.low > 399.0 && auto.high < 401.0, "auto range was ${auto.low}..${auto.high}")

    // And the point of it: the picture is no longer uniform.
    val distinct = (0 until 40).map { pixel(auto, it, 15) }.toSet()
    assertTrue(distinct.size > 4, "auto-ranged render still had only ${distinct.size} colours")
  }

  @Test
  fun `auto range survives a field with no values at all`() {
    val field = TestField("empty", ContinuousPalette(Ramps.VIRIDIS)) { _, _ -> Double.NaN }

    val map = renderer.render(field, view, RenderOptions(hillshade = false, autoRange = true))

    assertTrue(map.low < map.high, "an empty field must still yield a usable range")
  }

  @Test
  fun `flat ground is not tinted by the hillshade`() {
    val flat = DoubleArray(30 * 20) { 100.0 }

    val shade = Hillshade.shade(flat, 30, 20, metresPerPixel = 1.0)

    for (value in shade) {
      assertEquals(1.0, value, 1e-9)
    }
  }

  @Test
  fun `slopes facing the light are brighter than slopes facing away`() {
    // A moderate slope on purpose. Past about 45 degrees even the lit face falls below the flat
    // response, because it is then turned well away from a light sitting 45 degrees up.
    fun slope(sign: Double) = DoubleArray(30 * 20) { i -> sign * (i % 30) * 0.5 }

    // The default light is at azimuth 315 - the north-west - so a surface whose normal points west
    // catches it and the opposite face is in shadow.
    val lit = Hillshade.shade(slope(1.0), 30, 20, metresPerPixel = 1.0)
    val shadowed = Hillshade.shade(slope(-1.0), 30, 20, metresPerPixel = 1.0)

    val at = 10 * 30 + 15
    assertTrue(lit[at] > 1.0, "lit face was ${lit[at]}")
    assertTrue(shadowed[at] < 1.0, "shadowed face was ${shadowed[at]}")
  }

  @Test
  fun `hillshade responds to relief and not to absolute height`() {
    val low = DoubleArray(30 * 20) { i -> (i % 30) * 2.0 }
    val high = DoubleArray(30 * 20) { i -> 5000.0 + (i % 30) * 2.0 }

    val a = Hillshade.shade(low, 30, 20, metresPerPixel = 1.0)
    val b = Hillshade.shade(high, 30, 20, metresPerPixel = 1.0)

    for (i in a.indices) {
      assertTrue(abs(a[i] - b[i]) < 1e-9, "shading changed with the datum at $i")
    }
  }

  @Test
  fun `hillshading a field does not change where it has no data`() {
    val field = TestField("holed", ElevationPalette()) { x, y ->
      if (x < 0.0) Double.NaN else 200.0 + y / 20.0
    }

    val map = renderer.render(field, view, RenderOptions(hillshade = true))

    assertEquals(MapRenderer.NO_DATA, pixel(map, 0, 15))
  }

  @Test
  fun `rendering is a pure function of the field and the view`() {
    val field = TestField("noise", ElevationPalette()) { x, y -> (x * 0.7 + y * 1.3) % 500.0 }

    val once = renderer.render(field, view)
    val twice = renderer.render(field, view)

    for (py in 0 until 30) {
      for (px in 0 until 40) {
        assertEquals(pixel(once, px, py), pixel(twice, px, py), "pixel ($px,$py)")
      }
    }
  }
}
