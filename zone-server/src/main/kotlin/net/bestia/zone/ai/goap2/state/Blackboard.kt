package net.bestia.zone.ai.goap2.state

/**
 * The **live, mutable** memory of an agent (or the world). Unlike [WorldState],
 * which is a frozen snapshot used for planning, the blackboard is where
 * perception writes facts and where they decay over time via a time-to-live.
 *
 * Keeping this separate from [WorldState] is deliberate: TTL decay is a
 * perception concern and must not happen *during* an A* simulation, where we
 * apply hypothetical effects to hypothetical futures. When we want to plan, we
 * take a [snapshot] and let the planner work on that immutable value.
 *
 * This replaces the old mutable `State` class and its stubbed `appendState`.
 */
class Blackboard {

  private data class Entry(val value: Any?, var remainingTime: Float)

  private val entries = mutableMapOf<StateKey<*>, Entry>()

  /**
   * Store [value] under [key], refreshing its lifetime. A [retain] of
   * [PERMANENT] means the fact never decays.
   */
  fun <T> set(key: StateKey<T>, value: T, retain: Float = DEFAULT_RETAIN_TIME_SECONDS) {
    entries[key] = Entry(value, retain)
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> get(key: StateKey<T>): T? = entries[key]?.value as T?

  fun contains(key: StateKey<*>): Boolean = entries.containsKey(key)

  fun remove(key: StateKey<*>) {
    entries.remove(key)
  }

  /**
   * Advance time by [deltaSeconds], decrementing every non-permanent fact's
   * remaining lifetime and evicting anything that has expired.
   */
  fun tick(deltaSeconds: Float) {
    val iterator = entries.iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next().value
      if (entry.remainingTime == PERMANENT) continue
      entry.remainingTime -= deltaSeconds
      if (entry.remainingTime <= 0f) iterator.remove()
    }
  }

  /** Freeze the current contents into an immutable [WorldState] for planning. */
  fun snapshot(): WorldState =
    WorldState.Companion.from(entries.mapValues { it.value.value })

  /**
   * Snapshot this blackboard and layer [others] on top of it in order, so the
   * last board wins on conflict. Used by the planner to layer world -> team ->
   * individual memory into one state (see `Agent.snapshotState`); this is the
   * correct replacement for the old `appendState` stub.
   */
  fun snapshotMergedWith(vararg others: Blackboard): WorldState =
    others.fold(snapshot()) { acc, board -> acc.mergedWith(board.snapshot()) }

  companion object {
    const val DEFAULT_RETAIN_TIME_SECONDS = 600f

    /** Sentinel lifetime for facts that never decay. */
    const val PERMANENT = Float.POSITIVE_INFINITY
  }
}
