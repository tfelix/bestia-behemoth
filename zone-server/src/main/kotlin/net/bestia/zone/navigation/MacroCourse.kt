package net.bestia.zone.navigation

/**
 * A route over the macro graph: the nodes still to reach, in order, and the edge that leads to each.
 * `edges[i]` ends at `nodes[i]`; the first one starts at the node nearest where the traveller set off.
 */
data class MacroCourse(val nodes: List<Int>, val edges: List<Int>) {
  init {
    require(nodes.size == edges.size) { "Every node is reached by one edge" }
  }
}
