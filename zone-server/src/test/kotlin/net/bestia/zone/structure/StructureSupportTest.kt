package net.bestia.zone.structure

import net.bestia.worldgen.derived.Edges
import net.bestia.worldgen.voxel.Occupancy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StructureSupportTest {

  private val wood = StructureMaterial("WOOD", verticalLoss = 0.125, horizontalLoss = 0.2, minStability = 0.1)

  private fun block(x: Long, y: Long, z: Long): List<StructureCell> {
    return listOf(StructureCell(x, y, z, Occupancy.FULL, Edges.NONE))
  }

  @Test
  fun `a piece on the ground is fully stable, and each piece on top less so`() {
    val support = StructureSupport()
    support.add(1, wood, block(0, 0, 0), grounded = true)
    support.add(2, wood, block(0, 0, 1), grounded = false)
    support.add(3, wood, block(1, 0, 1), grounded = false)

    assertEquals(1.0, support.stabilityOf(1), 1e-9)
    assertEquals(0.875, support.stabilityOf(2), 1e-9, "rests on it")
    assertEquals(0.675, support.stabilityOf(3), 1e-9, "held from the side")
  }

  @Test
  fun `a piece touching nothing has no stability`() {
    assertEquals(0.0, StructureSupport().stabilityIfPlaced(wood, block(5, 5, 5), grounded = false), 1e-9)
  }

  @Test
  fun `a pillar put under a long reach makes it stable again`() {
    val support = StructureSupport()
    support.add(1, wood, block(0, 0, 0), grounded = true)
    for (x in 1L..4L) support.add(x + 1, wood, block(x, 0, 0), grounded = false)
    assertEquals(0.2, support.stabilityOf(5), 1e-9)

    support.add(9, wood, block(4, 0, -1), grounded = true)

    assertEquals(0.875, support.stabilityOf(5), 1e-9, "now it rests on the pillar")
  }

  @Test
  fun `taking the support away leaves what it held unsupported`() {
    val support = StructureSupport()
    support.add(1, wood, block(0, 0, 0), grounded = true)
    support.add(2, wood, block(0, 0, 1), grounded = false)
    support.add(3, wood, block(0, 0, 2), grounded = false)

    val unsupported = support.remove(1)

    assertEquals(setOf(2L, 3L), unsupported)
    assertTrue(support.stabilityOf(3) < wood.minStability)
  }

  @Test
  fun `restoring many at once settles them as adding one by one would`() {
    val support = StructureSupport()
    support.addAll(
      listOf(
        StructureSupport.PlacedPiece(3, wood, block(0, 0, 2), grounded = false),
        StructureSupport.PlacedPiece(2, wood, block(0, 0, 1), grounded = false),
        StructureSupport.PlacedPiece(1, wood, block(0, 0, 0), grounded = true)
      )
    )

    assertEquals(0.75, support.stabilityOf(3), 1e-9)
  }
}
