package net.bestia.worldgen.hydro

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `ChannelGauge`'s floor is what stops the "dashed line of water on sand" bug documented on the class itself -
 * a channel shallower than one voxel gets water only where its bed happens to cross a voxel boundary. These
 * tests exist so a regression to that bug (the `max()` direction flipped, or a floor constant dropped to zero)
 * fails here instead of being rediscovered by looking at a client screenshot.
 */
class ChannelGaugeTest {

  private val params = HydrologyParams()
  private val gauge = ChannelGauge(params, voxelSize = 1.0)

  @Test
  fun `zero discharge floors to the minimum gauge, never zero`() {
    assertEquals(params.minChannelWidthVoxels, gauge.widthOf(0.0))
    assertEquals(params.minChannelDepthVoxels, gauge.depthOf(0.0))
    assertTrue(gauge.shoulderOf(0.0) > 0.0)
  }

  @Test
  fun `negative discharge is coerced rather than producing a negative or NaN gauge`() {
    // pow(0.4) of a negative base is NaN in Kotlin, so this only stays finite because coerceAtLeast(0.0) runs
    // before the exponent is applied - a real regression here (reordering the clamp) would surface as NaN
    // reaching the voxel grid rather than as an exception.
    assertEquals(params.minChannelWidthVoxels, gauge.widthOf(-5.0))
    assertEquals(params.minChannelDepthVoxels, gauge.depthOf(-5.0))
    assertTrue(gauge.shoulderOf(-5.0).isFinite())
  }

  @Test
  fun `the depth floor binds for every headwater creek, which is the whole point of it`() {
    // The class's own measurement: the physical depth-discharge relation needs roughly 13 m3 per second before
    // it reaches one voxel, and nothing in a world of a few hundred kilometres carries that at most stations.
    // Below the floor's own crossover, depthOf must return exactly the floor - if the physical term ever crept
    // above it silently, some stations would go back to being sub-voxel deep and start flickering again.
    for (dischargeCubicMetresPerSecond in listOf(0.0, 0.5, 1.0, 3.0, 8.0)) {
      assertEquals(
        params.minChannelDepthVoxels,
        gauge.depthOf(dischargeCubicMetresPerSecond),
        "a $dischargeCubicMetresPerSecond m3/s creek should be at the depth floor, not the physical formula"
      )
    }
  }

  @Test
  fun `above the floor, width and depth follow the hydraulic geometry formulas exactly`() {
    // Large enough that neither floor binds, so what's left is a direct check of the coefficient*Q^exponent
    // shape the class's KDoc states as "the hydraulic geometry is right."
    val bigDischarge = 5_000.0

    val expectedWidth = params.widthCoefficient * bigDischarge.pow(0.5)
    val expectedDepth = params.depthCoefficient * bigDischarge.pow(0.4)
    val expectedShoulder = params.shoulderCoefficient * bigDischarge.pow(0.35)

    assertTrue(expectedWidth > params.minChannelWidthVoxels, "test discharge must clear the width floor")
    assertTrue(expectedDepth > params.minChannelDepthVoxels, "test discharge must clear the depth floor")

    assertEquals(expectedWidth, gauge.widthOf(bigDischarge), 1e-9)
    assertEquals(expectedDepth, gauge.depthOf(bigDischarge), 1e-9)
    assertEquals(maxOf(expectedWidth, expectedShoulder), gauge.shoulderOf(bigDischarge), 1e-9)
  }

  @Test
  fun `the shoulder is never narrower than the channel it floods around, at any discharge`() {
    // The KDoc's claim: shoulderOf already takes the max against widthOf, so a width floored to the minimum
    // gauge gets a shoulder at least as wide rather than a bank ending inside the channel's own bed.
    for (discharge in listOf(0.0, 1.0, 50.0, 500.0, 50_000.0)) {
      assertTrue(
        gauge.shoulderOf(discharge) >= gauge.widthOf(discharge),
        "shoulder narrower than the channel at discharge=$discharge"
      )
    }
  }

  @Test
  fun `voxelSize scales the floor but not the physical formula`() {
    // The floor is stated in voxels (minChannelWidthVoxels * voxelSize), so a coarser grid should raise the
    // floor in metres while leaving the exponent-based term - which is a physical relation, not a grid
    // property - completely alone.
    val coarse = ChannelGauge(params, voxelSize = 4.0)

    assertEquals(params.minChannelWidthVoxels * 4.0, coarse.widthOf(0.0))
    assertEquals(params.minChannelDepthVoxels * 4.0, coarse.depthOf(0.0))

    val bigDischarge = 5_000.0
    assertEquals(gauge.widthOf(bigDischarge), coarse.widthOf(bigDischarge), 1e-9)
  }
}
