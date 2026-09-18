package net.bestia.zone.world.ground

/**
 * Where one column's stamped marks come from. [GroundLayerSource]'s sibling, for marks with a shape.
 */
interface GroundStampSource {

  fun stampsAt(columnKey: Long): List<GroundStamp>
}
