package net.bestia.zone.structure

import net.bestia.worldgen.derived.Edges
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class StructureRasterizerTest {

  private val post = StructurePiece(
    StaticEntityKind.STRUCTURE_WALL,
    listOf(StructurePiece.Cell(x = 1, y = 0, z = 0, fill = Occupancy.FULL, edges = Edges.X_PLUS))
  )

  @Test
  fun `a wall is written on both sides of its border`() {
    val cells = StructureRasterizer.rasterize(post, Vec3L(10, 20, 5), quarterTurns = 0)

    assertEquals(
      setOf(
        StructureCell(11, 20, 5, Occupancy.FULL, Edges.X_PLUS),
        StructureCell(12, 20, 5, Occupancy.EMPTY, Edges.X_MINUS)
      ),
      cells.toSet()
    )
  }

  @Test
  fun `a quarter turn takes x+ to y-, cells and walls alike, as the client turns a model`() {
    val cells = StructureRasterizer.rasterize(post, Vec3L(10, 20, 5), quarterTurns = 1)

    assertEquals(
      setOf(
        StructureCell(10, 19, 5, Occupancy.FULL, Edges.Y_MINUS),
        StructureCell(10, 18, 5, Occupancy.EMPTY, Edges.Y_PLUS)
      ),
      cells.toSet()
    )
  }

  @Test
  fun `four quarter turns are none`() {
    assertEquals(
      StructureRasterizer.rasterize(post, Vec3L(0, 0, 0), 0).toSet(),
      StructureRasterizer.rasterize(post, Vec3L(0, 0, 0), 4).toSet()
    )
  }

  @Test
  fun `a yaw rounds to the nearest quarter turn`() {
    assertEquals(0, StructureRasterizer.quarterTurnsOf(0.1f))
    assertEquals(1, StructureRasterizer.quarterTurnsOf((PI / 2).toFloat()))
    assertEquals(2, StructureRasterizer.quarterTurnsOf(PI.toFloat()))
    assertEquals(3, StructureRasterizer.quarterTurnsOf((-PI / 2).toFloat()))
  }
}
