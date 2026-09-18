package net.bestia.zone.world.ground

/**
 * One mark left on the ground at a point: a footprint, a splatter.
 *
 * ### Why these are not cells in a [ColumnLevels]
 *
 * A print has a shape and a heading, and a grid of levels has neither - a print is about thirty centimetres
 * across against a one metre cell, so a grid can say *that* ground was walked but never *what walked it, which
 * way*. Keeping the record instead of the pixels also means how good it looks stays a client decision: the
 * same stamp renders as a darkened patch, a normal-mapped dent, or displaced geometry, and none of those
 * reaches the protocol.
 *
 * ### Two marks, one footfall
 *
 * A step writes a stamp *and* adds to the wear underneath it. They answer different questions and fade on
 * different clocks: the print is gone within the hour, the path it belongs to lasts for days.
 *
 * @property x world voxel position, absolute rather than column-local - a stamp near an edge legitimately
 *   overlaps its neighbour, and clipping it to the column it was filed under would cut prints in half
 * @property rotation heading in 256ths of a turn; movement is eight-connected, so only eight values occur
 * @property seed picks a variant, so a hundred prints are not one print a hundred times
 * @property atSecond the Bestia second it was made, so the client fades it without being told again
 */
data class GroundStamp(
  val x: Long,
  val y: Long,
  val layer: GroundLayer,
  val brush: StampBrush,
  val rotation: Int,
  val seed: Int,
  val atSecond: Long
) {

  init {
    require(rotation in 0..255) { "rotation is 256ths of a turn, was $rotation" }
  }
}
