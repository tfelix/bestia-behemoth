package net.bestia.zone.world.stream

/**
 * A tick's allowance of expensive slab computations. Mutable on purpose; one instance, shared.
 *
 * A slab is one 256 m vertical layer of chunks, a chunk's z index. The expensive part is working out which slabs
 * of a column hold a surface ([ChunkService.surfaceSlabsOf], cached per column after that). [ViewVolume] says
 * which slabs are offered.
 */
class SlabBudget(remaining: Int, private val maxNanos: Long = Long.MAX_VALUE) {

  var remaining = remaining
    private set

  private val started = System.nanoTime()

  /**
   * Whether the tick has spent its time as well as its count. A slab is world generation on fresh ground and
   * costs far more than a cache hit, so the count alone let four of them hold a tick for hundreds of
   * milliseconds. The first spend is always allowed, so a budget cannot starve.
   */
  private fun outOfTime(): Boolean =
    remaining < initial && System.nanoTime() - started >= maxNanos

  private val initial = remaining

  /** Set once a spend was refused: the tick had more to cost than it could afford. */
  var refused = false
    private set

  fun trySpend(): Boolean {
    if (remaining <= 0 || outOfTime()) {
      refused = true
      return false
    }
    remaining--
    return true
  }
}
