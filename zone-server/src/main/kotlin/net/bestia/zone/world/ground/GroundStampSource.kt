package net.bestia.zone.world.ground

/**
 * Where a column's shaped marks come from.
 *
 * [GroundLayerSource]'s twin, and here for the same reason: [GroundOverlayService] broadcasts stamps without
 * ever learning what one means. A separate interface rather than another method on that one because the two
 * travel in different messages and at completely different rates - see `ChunkGroundStampsSMSG`.
 */
interface GroundStampSource {

  /**
   * @return this column's stamps in wire form, or null when nothing has passed here - which is the answer for
   *   almost every column almost always
   */
  fun stampsAt(columnKey: Long): ByteArray?
}
