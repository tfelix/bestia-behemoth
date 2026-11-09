package net.bestia.zone.structure

import net.bestia.zone.entity.StaticEntityKind

/** The cells one kind of building piece fills, relative to where it is placed and before it is turned. */
data class StructurePiece(
  val kind: StaticEntityKind,
  val cells: List<Cell>
) {

  /**
   * @property fill how full the cell is from the bottom up, as an `Occupancy` byte
   * @property edges the walls on the cell's borders, as `Edges` bits
   */
  data class Cell(
    val x: Int,
    val y: Int,
    val z: Int,
    val fill: Int,
    val edges: Int
  )
}
