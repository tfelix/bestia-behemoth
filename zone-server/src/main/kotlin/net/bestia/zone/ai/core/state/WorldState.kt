package net.bestia.zone.ai.core.state

/**
 * An **immutable** snapshot of world/agent knowledge used during planning.
 *
 * Every mutating operation ([with], [without]) returns a *new* `WorldState`,
 * which is exactly what the forward A* search needs: it can generate successor
 * states by applying an action's effects without touching the original. Value
 * based [equals]/[hashCode] let the planner's closed set recognise when two
 * different action sequences reach the same world state and avoid re-expanding it.
 *
 * Two arrays sorted by [StateKey.id] rather than a map: the search makes a state per effect and hashes
 * each several times, so a copy is two array copies and the hash is computed once.
 *
 * Values are stored as `Any?` internally but are only ever read back through a
 * typed [StateKey], so callers never see the untyped arrays.
 */
class WorldState private constructor(
  /** Sorted by id; never written after construction, so states may share them. */
  private val keys: Array<StateKey<*>>,
  private val values: Array<Any?>,
) {

  /** 0 until first asked for. */
  private var hash = 0

  fun <T> get(key: StateKey<T>): T? {
    val index = indexOf(key)
    if (index < 0) return null

    @Suppress("UNCHECKED_CAST")
    return values[index] as T?
  }

  fun contains(key: StateKey<*>): Boolean {
    return indexOf(key) >= 0
  }

  /** All keys currently held, e.g. to diff two states when applying a plan step. */
  fun keys(): Set<StateKey<*>> {
    return keys.toSet()
  }

  fun <T> with(key: StateKey<T>, value: T): WorldState {
    val index = indexOf(key)
    if (index >= 0) {
      val replaced = values.copyOf()
      replaced[index] = value
      return WorldState(keys, replaced)
    }

    val at = -(index + 1)
    return WorldState(keys.inserted(at, key), values.inserted(at, value))
  }

  fun without(key: StateKey<*>): WorldState {
    val index = indexOf(key)
    if (index < 0) return this

    return WorldState(keys.removed(index), values.removed(index))
  }

  /** Returns a copy with every entry of [other] layered on top of this one. */
  fun mergedWith(other: WorldState): WorldState {
    if (other.keys.isEmpty()) return this
    if (keys.isEmpty()) return other

    val mergedKeys = ArrayList<StateKey<*>>(keys.size + other.keys.size)
    val mergedValues = ArrayList<Any?>(keys.size + other.keys.size)
    var mine = 0
    var theirs = 0

    while (mine < keys.size || theirs < other.keys.size) {
      val myId = if (mine < keys.size) keys[mine].id else Int.MAX_VALUE
      val theirId = if (theirs < other.keys.size) other.keys[theirs].id else Int.MAX_VALUE

      when {
        myId < theirId -> {
          mergedKeys.add(keys[mine])
          mergedValues.add(values[mine++])
        }
        myId > theirId -> {
          mergedKeys.add(other.keys[theirs])
          mergedValues.add(other.values[theirs++])
        }
        else -> {
          mergedKeys.add(keys[mine++])
          mergedValues.add(other.values[theirs++])
        }
      }
    }

    return WorldState(mergedKeys.toTypedArray(), mergedValues.toTypedArray())
  }

  val size: Int get() = keys.size

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is WorldState || keys.size != other.keys.size || hashCode() != other.hashCode()) return false

    for (i in keys.indices) {
      if (keys[i].id != other.keys[i].id || values[i] != other.values[i]) return false
    }
    return true
  }

  override fun hashCode(): Int {
    if (hash == 0) {
      var h = 1
      for (i in keys.indices) {
        h = 31 * h + keys[i].id
        h = 31 * h + (values[i]?.hashCode() ?: 0)
      }
      hash = if (h == 0) 1 else h
    }
    return hash
  }

  override fun toString(): String {
    return keys.indices.joinToString(prefix = "WorldState{", postfix = "}") { "${keys[it]}=${values[it]}" }
  }

  /** The key's index, or `-(insertion point) - 1` when it is absent, like [java.util.Arrays.binarySearch]. */
  private fun indexOf(key: StateKey<*>): Int {
    var low = 0
    var high = keys.size - 1
    while (low <= high) {
      val mid = (low + high) ushr 1
      val midId = keys[mid].id
      when {
        midId < key.id -> low = mid + 1
        midId > key.id -> high = mid - 1
        else -> return mid
      }
    }
    return -(low + 1)
  }

  companion object {
    val EMPTY = WorldState(emptyArray(), emptyArray())

    fun of(vararg pairs: Pair<StateKey<*>, Any?>): WorldState {
      return pairs.fold(EMPTY) { state, (key, value) ->
        @Suppress("UNCHECKED_CAST")
        state.with(key as StateKey<Any?>, value)
      }
    }

    fun from(values: Map<StateKey<*>, Any?>): WorldState {
      val sorted = values.entries.sortedBy { it.key.id }
      return WorldState(
        Array(sorted.size) { sorted[it].key },
        Array(sorted.size) { sorted[it].value },
      )
    }

    private inline fun <reified T> Array<T>.inserted(at: Int, element: T): Array<T> {
      val result = arrayOfNulls<T>(size + 1)
      System.arraycopy(this, 0, result, 0, at)
      result[at] = element
      System.arraycopy(this, at, result, at + 1, size - at)
      @Suppress("UNCHECKED_CAST")
      return result as Array<T>
    }

    private inline fun <reified T> Array<T>.removed(at: Int): Array<T> {
      val result = arrayOfNulls<T>(size - 1)
      System.arraycopy(this, 0, result, 0, at)
      System.arraycopy(this, at + 1, result, at, size - at - 1)
      @Suppress("UNCHECKED_CAST")
      return result as Array<T>
    }
  }
}
