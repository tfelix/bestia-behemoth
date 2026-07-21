package net.bestia.zone.ai.goap2.state

/**
 * An **immutable** snapshot of world/agent knowledge used during planning.
 *
 * Every mutating operation ([with], [without]) returns a *new* `WorldState`,
 * which is exactly what the forward A* search needs: it can generate successor
 * states by applying an action's effects without touching the original. Value
 * based [equals]/[hashCode] over the backing map let the planner's closed set
 * recognise when two different action sequences reach the same world state and
 * avoid re-expanding it.
 *
 * Values are stored as `Any?` internally but are only ever read back through a
 * typed [StateKey], so callers never see the untyped map.
 */
class WorldState private constructor(
  private val values: Map<StateKey<*>, Any?>
) {

  @Suppress("UNCHECKED_CAST")
  fun <T> get(key: StateKey<T>): T? = values[key] as T?

  fun contains(key: StateKey<*>): Boolean = values.containsKey(key)

  /** All keys currently held, e.g. to diff two states when applying a plan step. */
  fun keys(): Set<StateKey<*>> = values.keys

  fun <T> with(key: StateKey<T>, value: T): WorldState =
    WorldState(values + (key to value))

  fun without(key: StateKey<*>): WorldState =
    if (values.containsKey(key)) WorldState(values - key) else this

  /** Returns a copy with every entry of [other] layered on top of this one. */
  fun mergedWith(other: WorldState): WorldState =
    WorldState(values + other.values)

  val size: Int get() = values.size

  override fun equals(other: Any?): Boolean =
    this === other || (other is WorldState && values == other.values)

  override fun hashCode(): Int = values.hashCode()

  override fun toString(): String =
    values.entries.joinToString(prefix = "WorldState{", postfix = "}") { (k, v) -> "$k=$v" }

  companion object {
    val EMPTY = WorldState(emptyMap())

    fun of(vararg pairs: Pair<StateKey<*>, Any?>): WorldState =
      WorldState(pairs.toMap())

    fun from(values: Map<StateKey<*>, Any?>): WorldState =
      WorldState(values.toMap())
  }
}
