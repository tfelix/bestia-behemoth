package net.bestia.worldgen.voxel

/**
 * How much of the vegetation a place would otherwise grow is left standing, in `[0,1]`.
 *
 * The companion of [PropSite], and a different question from it. `PropSite` answers *may anything stand on
 * this spot* - somebody paved it, roofed it, bridged it or opened a hole under it - and every prop kind
 * agrees about the answer. This one is about **how many**, it is asked only of trees, and it has no opinion
 * about landmarks: a point of interest the world tier decided exists must not be deleted because it happens
 * to stand in a town.
 *
 * Keeping the two apart is the whole reason this type exists rather than a second method on `PropSite`. That
 * interface is consumed by the crystals, the ground cover, the wound spires, the aetherite and `PoiProps`,
 * whose own documentation calls a refusal "not a second opinion but a physical impossibility".
 *
 * Like `PropSite`, an implementation must be a **pure function of the position asked about** - never of the
 * chunk being built or the column being filled. A crown is drawn by whichever chunk owns the trunk, and the
 * chunk next door has to reach the same verdict about the same tree.
 */
fun interface TreeRetention {
  fun retentionAt(worldX: Double, worldY: Double): Double

  companion object {
    /** What open country gets: the climate decides alone. */
    val EVERYTHING = TreeRetention { _, _ -> 1.0 }
  }
}
