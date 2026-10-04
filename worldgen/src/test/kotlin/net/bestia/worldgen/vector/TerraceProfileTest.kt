package net.bestia.worldgen.vector

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `RadialProfiles.terrace`'s cut floor: a town graded below the river through it must not cut the banks
 * under the water line. Grimhold's river stood eight metres above its own town before the floor existed.
 */
class TerraceProfileTest {

  private val target = 300.0
  private val floor = 320.0
  private val terrace = RadialProfiles.terrace(target, maxCut = 9.0, maxFill = 2.5, cutFloor = floor)

  @Test
  fun `ground above the floor is cut down to it at most`() {
    assertEquals(floor, terrace.heightAt(0.0, floor + 4.0))
    // Far enough above that the ordinary cut limit is the one that binds.
    assertEquals(floor + 20.0 - 9.0, terrace.heightAt(0.0, floor + 20.0))
  }

  @Test
  fun `ground just under the floor is not cut at all`() {
    // The river's own shoulder leaves the bank top a few centimetres under the floor. Testing the floor as
    // `base >= cutFloor` let that fall through to the full cut and reproduced the slab.
    assertEquals(floor - 0.05, terrace.heightAt(0.0, floor - 0.05))
    assertEquals(floor - 6.0, terrace.heightAt(0.0, floor - 6.0))
  }

  @Test
  fun `fill is untouched by the floor`() {
    assertEquals(target - 8.0 + 2.5, terrace.heightAt(0.0, target - 8.0))
    assertEquals(target, terrace.heightAt(0.0, target - 1.0))
  }

  @Test
  fun `no floor is the plain terrace`() {
    val plain = RadialProfiles.terrace(target, maxCut = 9.0, maxFill = 2.5)
    assertEquals(target + 11.0, plain.heightAt(0.0, target + 20.0))
    assertEquals(target, plain.heightAt(0.0, target + 4.0))
  }

  @Test
  fun `continuous in base across the floor`() {
    var previous = terrace.heightAt(0.0, target - 20.0)
    var base = target - 20.0
    while (base < floor + 30.0) {
      base += 0.01
      val height = terrace.heightAt(0.0, base)
      assertTrue(abs(height - previous) <= 0.01 + 1e-9, "step of ${height - previous} at base $base")
      previous = height
    }
  }
}
