package net.bestia.zone.cartography.coverage

/**
 * How much of one area a [Coverage] holds: all of it, none of it, or some named shape in between.
 *
 * The three cases exist because they are three different pieces of work, and separating them is what keeps
 * per-player map tiles affordable:
 *
 * - [Full] serves the base tile untouched. Every well-charted player shares one cached entry for it, and no
 *   masking runs at all - the dominant case once somebody has charted the region they play in.
 * - [None] is a 404. The tile is never rendered, so nothing about that ground can leak, not even its file size.
 * - [Partial] carries a digest of *only the bits inside the area*, which is what makes it a useful cache key:
 *   two players exploring the same frontier ask for the same masked tile and get the same one, even though
 *   their charts differ everywhere else in the world.
 *
 * Returned as a type rather than as a `Long` with reserved sentinel values, because the sentinels would have to
 * be values a real digest might also produce, and the collision would be invisible.
 */
sealed interface AreaCoverage {

  /** Every cell of the area is charted. */
  object Full : AreaCoverage

  /** No cell of the area is charted. */
  object None : AreaCoverage

  /**
   * Some cells are charted, and [digest] identifies which.
   *
   * A 64-bit mix of the covered bits and their positions. It is a cache key and not a security boundary, but
   * the two are close enough here to be worth stating: a collision would serve a mask built from a different
   * chart set, so the width and the mixing matter. Both ends are 64-bit and well mixed, which puts a collision
   * far below the rate at which other things go wrong, and the failure is a *different* mask rather than an
   * absent one.
   */
  data class Partial(val digest: Long) : AreaCoverage
}
