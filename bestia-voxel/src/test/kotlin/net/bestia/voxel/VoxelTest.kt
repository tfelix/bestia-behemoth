package net.bestia.voxel

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class VoxelTest {

  @Test
  fun `voxel percent returns the correct amount in percent`() {
    val v = Voxel.of(1, 2f)
    Assertions.assertEquals(1f, v.occupancyPercent, 0.001f)

    val v2 = Voxel.of(1, 0f)
    Assertions.assertEquals(0f, v2.occupancyPercent, 0.001f)

    val v3 = Voxel.of(1, 0.5f)
    Assertions.assertEquals(0.5f, v3.occupancyPercent, 0.001f)
  }

  @Test
  fun `creating a voxel of material 'air' always results in occupancy 0`() {
    val v = Voxel.of(0, 1f)
    Assertions.assertEquals(0f, v.occupancyPercent, 0.001f)
  }
}