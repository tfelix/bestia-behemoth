package de.tfelix.bestia.worldgen.map

interface Chunk {
  val identifier: String
  val width: Int
  val height: Int

  /**
   * Iterator to iterate over all the contained map coordinates contained in
   * this bunch of map..
   *
   * @return The iterator to iterate all over the contained coordinates in local space.
   */
  fun getIterator(): Iterator<Point>

  fun toGlobalCoordinates(localCords: Point): Point
}