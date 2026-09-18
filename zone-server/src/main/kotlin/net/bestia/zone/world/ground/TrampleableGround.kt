package net.bestia.zone.world.ground

/**
 * What the ground at one tile does when something walks over it.
 *
 * An interface with one real implementation, for the reason `BurnableGround` and `ForageGround` share: the real
 * answer needs a generated world, and a test asking "does a road stay unworn" should not have to build one to
 * find out.
 *
 * ### Wearing and taking a print are separate questions
 *
 * They disagree on most ground, which is why there are two methods rather than one number. Snow takes a deep
 * print and never wears bare - there is no grass on it to kill. A hard dirt track is the opposite: it is
 * already bare, it compacts further under traffic, and nothing leaves an impression in it worth drawing. Asking
 * one question and using the answer for both would give beaches permanent brown paths and meadows no tracks.
 */
interface TrampleableGround {

  /** `0` for ground that never shows a path, up to `1` for ground that wears bare quickly. */
  fun wearAt(voxelX: Long, voxelY: Long): Double

  /** `0` for ground nothing leaves a mark in, up to `1` for ground that holds a deep print. */
  fun impressionAt(voxelX: Long, voxelY: Long): Double
}
