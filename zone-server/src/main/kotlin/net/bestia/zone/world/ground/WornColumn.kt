package net.bestia.zone.world.ground

/**
 * One chunk column's wear: how bare each square metre is, and when it was last aged.
 *
 * ### Decay is derived, not ticked
 *
 * [ageTo] takes the level down by however much time has passed, so a path sitting untouched in an empty
 * region costs nothing at all until somebody asks about it - the `Scar.visible` trick, arrived at from the
 * other direction. It also means the sweep cadence and the fade duration are independent: running it once a
 * minute or once an hour reaches the same level at the same moment.
 *
 * ### The remainder is kept, or nothing ever fades
 *
 * A fully worn cell fading over three Bestia days moves about a quarter of a level a minute, and an integer
 * subtraction of a quarter is nothing at all - so a naive per-pass decay would leave every path permanent.
 * [ageTo] therefore advances [lastDecayedSecond] only by the time the levels it actually removed account for,
 * and the leftover seconds are still owed on the next pass.
 */
class WornColumn(
  val levels: ColumnLevels,
  var lastDecayedSecond: Long,
) {

  /** Whether this column has unsaved changes. Cleared by whoever writes it out. */
  var dirty: Boolean = false

  val isEmpty: Boolean
    get() {
      return levels.isEmpty
    }

  /**
   * Ages the column forward to [nowSecond], given how long a full level takes to fade.
   *
   * @return true if a level the client would draw changed, which is the only thing worth re-announcing for
   */
  fun ageTo(nowSecond: Long, fadeSeconds: Long): Boolean {
    val elapsed = nowSecond - lastDecayedSecond
    if (elapsed <= 0) return false

    val steps = elapsed * ColumnLevels.MAX_LEVEL / fadeSeconds
    if (steps <= 0) return false

    // Only the time those steps paid for. See the class note: rounding this up to `elapsed` would discard the
    // remainder every pass, and at a minute a pass that is most of the fade.
    lastDecayedSecond += steps * fadeSeconds / ColumnLevels.MAX_LEVEL

    val redraw = levels.decayBy(steps.toInt())
    if (redraw) dirty = true

    return redraw
  }
}
