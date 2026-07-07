package net.bestia.zone.ecs2

/**
 * A dependency-free primitive `long -> int` open-addressing hash map (linear
 * probing with backward-shift deletion). It backs the sparse index of every
 * [ComponentStore] so that mapping an (arbitrary, snowflake) entity id to a
 * dense array slot never boxes and produces no per-lookup garbage.
 *
 * Not thread-safe. All mutation happens on the ECS tick thread.
 *
 * Note: this can be swapped 1:1 for `it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap`
 * should a fastutil dependency ever be added.
 */
class Long2IntOpenHashMap(
  initialCapacity: Int = 16,
  private val loadFactor: Float = 0.7f,
) {
  private var keys = LongArray(tableSizeFor(initialCapacity))
  private var values = IntArray(keys.size)
  private var used = BooleanArray(keys.size)
  private var mask = keys.size - 1
  private var threshold = (keys.size * loadFactor).toInt()

  var size: Int = 0
    private set

  private fun slotFor(key: Long): Int {
    var i = spread(key) and mask
    while (used[i] && keys[i] != key) i = (i + 1) and mask
    return i
  }

  fun get(key: Long): Int {
    val i = slotFor(key)
    return if (used[i]) values[i] else ABSENT
  }

  fun containsKey(key: Long): Boolean = used[slotFor(key)]

  /** Returns the previous value or [ABSENT] if the key was not present. */
  fun put(key: Long, value: Int): Int {
    val i = slotFor(key)
    if (used[i]) {
      val old = values[i]
      values[i] = value
      return old
    }
    used[i] = true
    keys[i] = key
    values[i] = value
    size++
    if (size >= threshold) resize()
    return ABSENT
  }

  /** Removes the key and returns its value, or [ABSENT] if it was not present. */
  fun remove(key: Long): Int {
    val start = slotFor(key)
    if (!used[start]) return ABSENT
    val old = values[start]

    // Backward-shift deletion (fastutil style): fill the hole by moving
    // subsequent entries whose ideal slot is not between the hole and them.
    var last = start
    var pos = (start + 1) and mask
    while (used[pos]) {
      val slot = spread(keys[pos]) and mask
      val canMove = if (last <= pos) (last >= slot || slot > pos) else (last >= slot && slot > pos)
      if (canMove) {
        keys[last] = keys[pos]
        values[last] = values[pos]
        last = pos
      }
      pos = (pos + 1) and mask
    }
    used[last] = false
    size--
    return old
  }

  fun clear() {
    used.fill(false)
    size = 0
  }

  private fun resize() {
    val oldKeys = keys
    val oldValues = values
    val oldUsed = used
    val newSize = keys.size shl 1
    keys = LongArray(newSize)
    values = IntArray(newSize)
    used = BooleanArray(newSize)
    mask = newSize - 1
    threshold = (newSize * loadFactor).toInt()
    size = 0
    for (i in oldKeys.indices) {
      if (oldUsed[i]) put(oldKeys[i], oldValues[i])
    }
  }

  companion object {
    /** Sentinel returned when a key is absent. Dense slots are always >= 0. */
    const val ABSENT = -1

    private fun tableSizeFor(capacity: Int): Int {
      var n = 2
      while (n < capacity) n = n shl 1
      return n
    }

    /** murmur3 fmix64 finalizer — good avalanche for snowflake-shaped keys. */
    private fun spread(key: Long): Int {
      var h = key
      h = (h xor (h ushr 33)) * (0xff51afd7ed558ccdUL).toLong()
      h = (h xor (h ushr 33)) * (0xc4ceb9fe1a85ec53UL).toLong()
      h = h xor (h ushr 33)
      return h.toInt()
    }
  }
}
