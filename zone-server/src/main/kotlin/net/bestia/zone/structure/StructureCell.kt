package net.bestia.zone.structure

/** One cell a placed piece fills, at a global voxel position. Fill and edges as in [StructurePiece.Cell]. */
data class StructureCell(
  val x: Long,
  val y: Long,
  val z: Long,
  val fill: Int,
  val edges: Int
)
