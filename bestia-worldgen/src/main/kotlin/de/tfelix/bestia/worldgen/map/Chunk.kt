package de.tfelix.bestia.worldgen.map

interface Chunk {
  val identifier: String
  val width: Int
  val height: Int

  /**
   * Iterator to iterate over all the contained map coordinates contained in
   * this bunch of map.
   *
   * @param scale The scale determines the iterated points, a scale of 2 indicates only every 2 points an iterator
   * is returned.
   *
   * @return The iterator to iterate all over the contained coordinates in local space.
   */
  fun getIterator(scale: Int = 1): Iterator<Point>

  fun toGlobalCoordinates(localCords: Point): Point
}