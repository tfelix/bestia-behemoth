package net.bestia.worldgen.fields

/**
 * A binary min-heap of `(double key, int value)` pairs, ordered by key and then by value.
 *
 * Exists for Priority-Flood, which pushes and pops every cell in the world - sixteen million of them
 * on a full-size map, several times over as erosion iterates. `java.util.PriorityQueue<Int>` boxes
 * every entry and would dominate the cost of the most expensive stage in the pipeline.
 *
 * The tie-break on the value is not an optimisation, it is a correctness requirement. Two cells at
 * exactly the same elevation are common - a flat plain, a filled lake surface - and if the heap pops
 * them in an order that depends on how the array happened to be laid out, two runs of the same world
 * produce different flow directions across the flat, and therefore different rivers.
 */
class DoubleIntHeap(initialCapacity: Int = 64) {

  private var keys = DoubleArray(maxOf(1, initialCapacity))
  private var values = IntArray(maxOf(1, initialCapacity))

  var size: Int = 0
    private set

  val isEmpty get() = size == 0

  fun clear() {
    size = 0
  }

  fun push(key: Double, value: Int) {
    if (size == keys.size) grow()

    var i = size++
    keys[i] = key
    values[i] = value

    while (i > 0) {
      val parent = (i - 1) shr 1
      if (less(i, parent)) {
        swap(i, parent)
        i = parent
      } else {
        break
      }
    }
  }

  /** Removes and returns the value with the smallest key. */
  fun pop(): Int {
    check(size > 0) { "Cannot pop an empty heap" }

    val top = values[0]
    size--
    if (size > 0) {
      keys[0] = keys[size]
      values[0] = values[size]

      var i = 0
      while (true) {
        val left = 2 * i + 1
        if (left >= size) break
        val right = left + 1
        val smaller = if (right < size && less(right, left)) right else left
        if (less(smaller, i)) {
          swap(i, smaller)
          i = smaller
        } else {
          break
        }
      }
    }

    return top
  }

  private fun less(a: Int, b: Int): Boolean {
    val ka = keys[a]
    val kb = keys[b]
    if (ka != kb) return ka < kb
    return values[a] < values[b]
  }

  private fun swap(a: Int, b: Int) {
    val k = keys[a]
    keys[a] = keys[b]
    keys[b] = k
    val v = values[a]
    values[a] = values[b]
    values[b] = v
  }

  private fun grow() {
    val capacity = keys.size * 2
    keys = keys.copyOf(capacity)
    values = values.copyOf(capacity)
  }
}
