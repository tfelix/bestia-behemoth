package net.bestia.worldgen.derived

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.VoxelChunk
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the forty-five degree walkability rule to the constants that actually implement it.
 *
 * The rule is enforced *here*, at the chunk tier, and not by the macro navigation graph - which runs at a
 * kilometre per cell and cannot resolve a cliff inside one. What enforces it is
 * [AgentProfile.maxStep] of one voxel against a horizontal run of one voxel, and `tan(45 degrees)` being
 * exactly one.
 *
 * That is two independent constants agreeing, which is worth a test precisely because nothing in the code says
 * they must: raising `maxStep` to 1.2 for a more athletic creature, or changing `voxelSize`, would silently
 * reopen every cliff in the world to NPC pathing with no compile error and no obvious symptom.
 */
class WalkableSlopeTest {

  private val voxelSize = 1.0

  @Test
  fun `the default step height is exactly the forty-five degree tangent`() {
    // The relationship in one line. If this fails, the two numbers below have drifted apart and the walkability
    // cutoff is no longer forty-five degrees - whatever the documentation says.
    val stepPerRun = AgentProfile().maxStep / voxelSize

    assertEquals(tan(Math.toRadians(45.0)), stepPerRun, 1e-9)
  }

  @Test
  fun `a rise within one voxel is walkable and a steeper one is not`() {
    val agent = AgentProfile()

    // Both sides of the cutoff, tested through the function the pathfinder actually calls.
    val gentle = tileWithStep(rise = 0.9)
    val steep = tileWithStep(rise = 1.4)

    assertTrue(
      gentle.stepTarget(1, 0, fromSurface = 1.0) >= 0.0,
      "a rise of 0.9 voxels is under the ${agent.maxStep}-voxel step limit and must be walkable"
    )
    assertTrue(
      steep.stepTarget(1, 0, fromSurface = 1.0) < 0.0,
      "a rise of 1.4 voxels is over forty-five degrees and must not be walkable"
    )
  }

  /**
   * A two-column chunk whose second column's floor stands [rise] voxels above the first's.
   *
   * Built as voxels rather than by constructing a [WalkableTile] directly, so the span-finding is exercised
   * too - the surface heights it reports are what the step test compares, fractional occupancy included.
   */
  private fun tileWithStep(rise: Double): WalkableTile {
    // The three-argument constructor gives an all-air chunk, which is the blank slate wanted here.
    val chunk = VoxelChunk(ChunkPos(0, 0, 0), size = 4, height = 8)

    // Column (0,0): floor at voxel 0, so its surface is 1.0 once the voxel is full.
    chunk.set(0, 0, 0, BlockType.GRANITE, Occupancy.FULL)

    // Column (1,0): floor raised by `rise`. A whole voxel plus a fraction, which is how a real slope arrives.
    val fullVoxels = Math.floor(rise).toInt()
    for (z in 0..fullVoxels) {
      chunk.set(1, 0, z, BlockType.GRANITE, Occupancy.FULL)
    }
    val fraction = rise - fullVoxels
    if (fraction > 0.0) {
      chunk.set(1, 0, fullVoxels + 1, BlockType.GRANITE, Occupancy.of(fraction))
    }

    return WalkableTile.of(chunk, AgentProfile())
  }
}
