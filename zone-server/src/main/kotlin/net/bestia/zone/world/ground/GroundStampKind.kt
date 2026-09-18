package net.bestia.zone.world.ground

/**
 * A mark with a shape and a heading, as opposed to a level on a grid.
 *
 * ### Why stamps exist beside [GroundLayer]
 *
 * A layer answers "how worn is this square metre", which a grid of levels expresses exactly. A footprint is a
 * different question: it has an outline, it points somewhere, and two of them a metre apart are two prints
 * rather than one deeper one. No grid of levels can say that, so stamps are their own record - see
 * [ColumnStamps] for what one costs.
 *
 * ### [wireId] is a wire contract
 *
 * Never reused, so a kind can be retired without renumbering the ones after it, and matched by number rather
 * than by name on the client - a rename should be a compile error or nothing, never a different shape.
 */
enum class GroundStampKind(val wireId: Int) {

  /**
   * Something walked here.
   *
   * The only kind so far, and deliberately not split by foot shape yet: nothing on the write path knows
   * whether a boot or a paw made it. [ColumnStamps] carries the discriminator regardless, so splitting it is
   * an entry here and a brush on the client rather than a protocol change.
   */
  FOOTPRINT(wireId = 1);

  companion object {

    fun ofWireId(wireId: Int): GroundStampKind? {
      return entries.firstOrNull { it.wireId == wireId }
    }
  }
}
