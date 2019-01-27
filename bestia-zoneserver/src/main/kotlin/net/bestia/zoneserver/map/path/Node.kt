package net.bestia.zoneserver.map.path

/**
 * Generic path node implementation. It must give some generic information about
 * a path in oder for the path finder to work correctly. Node objects are used
 * within hash maps and sets and thus should implement the
 * [.equals] and [.hashCode] method.
 *
 * @author Thomas Felix
 *
 */
data class Node<T>(
    val self: T
) {
  var parent: Node<T>? = null
  var ownCost: Float = 0f

  private var completeCost = Float.NaN

  /**
   * Walking cost of this current node.
   *
   * @return
   */
  val nodeCost: Float
    get() {
      if (completeCost == Float.NaN) {
        return completeCost
      }

      return ownCost + (parent?.nodeCost ?: 0f)
    }
}
