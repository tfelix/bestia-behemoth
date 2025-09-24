package net.bestia.zone.geometry

interface Vec3<T : Number> {
  /**
   * The X coordinate of this point.
   *
   * @return X
   */
  val x: T

  /**
   * The Y coordinate of this point.
   *
   * @return Y
   */
  val y: T

  val z: T
}
