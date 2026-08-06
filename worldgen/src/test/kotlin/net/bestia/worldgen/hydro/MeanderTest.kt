package net.bestia.worldgen.hydro

import net.bestia.worldgen.fields.Noise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `c*c*(3-2c)` clamped to `[0,1]` - `PolylineFeature.smoothstep` is `internal`, so replicated rather than relied on. */
private fun smoothstep(t: Double): Double {
  val c = t.coerceIn(0.0, 1.0)
  return c * c * (3.0 - 2.0 * c)
}

/**
 * `Meander` is "the single most important consequence of the three-representation split" per its own class
 * KDoc, and the one property it calls out as **not optional** - the taper reaching exactly zero at both reach
 * ends - has no dedicated test anywhere in the suite before this file. A violated taper reproduces the very
 * chunk-seam bug this design exists to avoid: a river that steps sideways at every confluence.
 */
class MeanderTest {

  @Test
  fun `the offset is exactly zero at both ends of the reach`() {
    val length = 4_000.0
    val taperLength = 300.0

    // A tolerance rather than bare equality: the taper is exactly 0.0 at both ends, but 0.0 times a negative
    // noise sample is IEEE -0.0, which boxed Double.equals (what a bare assertEquals uses) treats as unequal
    // to 0.0 even though -0.0 == 0.0 is true for the primitive. The property under test is magnitude, not sign.
    assertEquals(0.0, Meander.offset(1L, 0.0, length, amplitude = 40.0, wavelength = 500.0, taperLength), 1e-12)
    assertEquals(
      0.0,
      Meander.offset(1L, length, length, amplitude = 40.0, wavelength = 500.0, taperLength),
      1e-12
    )
  }

  @Test
  fun `the offset matches the noise blend and taper formula exactly`() {
    // A worked example against first principles, rather than a bound - `offset` has no free parameter left
    // unaccounted for once the same two noise samples and the same taper are computed independently here.
    val seed = 7L
    val s = 120.0
    val length = 4_000.0
    val amplitude = 40.0
    val wavelength = 500.0
    val taperLength = 300.0

    val primary = Noise.gradient2d(seed, s / wavelength, 0.37)
    val secondary = Noise.gradient2d(seed + 1L, s / (wavelength * 0.41), 0.37)
    val blend = (primary + secondary * 0.45) / (1.0 + 0.45)
    val taper = smoothstep(s / taperLength) // s < length - s here, so fromEnd is s itself

    val expected = blend * amplitude * taper

    assertEquals(expected, Meander.offset(seed, s, length, amplitude, wavelength, taperLength), 1e-9)
  }

  @Test
  fun `disabling amplitude or wavelength turns the offset off outright`() {
    assertEquals(0.0, Meander.offset(1L, 500.0, 4_000.0, amplitude = 0.0, wavelength = 500.0, taperLength = 300.0))
    assertEquals(0.0, Meander.offset(1L, 500.0, 4_000.0, amplitude = 40.0, wavelength = 0.0, taperLength = 300.0))
    assertEquals(0.0, Meander.offset(1L, 500.0, 4_000.0, amplitude = -5.0, wavelength = 500.0, taperLength = 300.0))
  }

  @Test
  fun `the offset never exceeds the requested amplitude, at any arc length`() {
    // Two octaves of [-1,1] noise blended with weights that sum to 1 (1 and SECONDARY_WEIGHT, divided by
    // 1+SECONDARY_WEIGHT) cannot exceed 1 in magnitude before the amplitude and taper are applied, and the
    // taper only ever multiplies by something in [0,1]. A regression that dropped that normalisation would
    // make a river wander further than the geometry it's supposed to describe.
    val amplitude = 25.0
    var s = 0.0
    while (s <= 4_000.0) {
      val value = Meander.offset(42L, s, 4_000.0, amplitude, wavelength = 350.0, taperLength = 250.0)
      assertTrue(kotlin.math.abs(value) <= amplitude + 1e-9, "offset $value exceeded amplitude $amplitude at s=$s")
      s += 137.0 // an arc-length step that shares no common factor with the wavelength or taper, deliberately
    }
  }

  @Test
  fun `the same seed and arc length always give the same offset`() {
    val a = Meander.offset(99L, 1234.5, 4_000.0, 30.0, 400.0, 200.0)
    val b = Meander.offset(99L, 1234.5, 4_000.0, 30.0, 400.0, 200.0)
    assertEquals(a, b)
  }

  @Test
  fun `different seeds meander differently`() {
    val a = Meander.offset(1L, 1500.0, 4_000.0, 30.0, 400.0, 200.0)
    val b = Meander.offset(2L, 1500.0, 4_000.0, 30.0, 400.0, 200.0)
    assertTrue(a != b, "two different seeds produced identical meander at the same station")
  }

  @Test
  fun `a zero taper length disables tapering entirely, even one step from the very end`() {
    // fromEnd never reaches taperLength when taperLength itself is zero, so the class special-cases it to
    // "always full amplitude" (taper == 1) rather than dividing by zero - checked exactly, not just as a
    // nonzero bound, since taper == 1 has a precise value to compare against.
    val seed = 5L
    val s = 0.5
    val wavelength = 400.0
    val amplitude = 30.0

    val primary = Noise.gradient2d(seed, s / wavelength, 0.37)
    val secondary = Noise.gradient2d(seed + 1L, s / (wavelength * 0.41), 0.37)
    val expected = (primary + secondary * 0.45) / (1.0 + 0.45) * amplitude

    assertEquals(expected, Meander.offset(seed, s, 4_000.0, amplitude, wavelength, taperLength = 0.0), 1e-9)
  }

  @Test
  fun `amplitude grows with channel width and is suppressed by slope`() {
    val flat = Meander.amplitudeFor(width = 10.0, slope = 0.0, widthFactor = 3.0, cap = 1_000.0)
    val wider = Meander.amplitudeFor(width = 20.0, slope = 0.0, widthFactor = 3.0, cap = 1_000.0)
    assertTrue(wider > flat, "a wider channel should meander further on flat ground")

    // The class's own stated calibration: at slope 0.02 the confinement term is exactly one half
    // (1 / (1 + 0.02 * 50) == 1 / 2), so amplitude at that slope must be exactly half of the flat-ground value.
    val onASlope = Meander.amplitudeFor(width = 10.0, slope = 0.02, widthFactor = 3.0, cap = 1_000.0)
    assertEquals(flat / 2.0, onASlope, 1e-9)
  }

  @Test
  fun `amplitude never exceeds the cap regardless of width`() {
    val huge = Meander.amplitudeFor(width = 10_000.0, slope = 0.0, widthFactor = 3.0, cap = 50.0)
    assertEquals(50.0, huge)
  }
}
