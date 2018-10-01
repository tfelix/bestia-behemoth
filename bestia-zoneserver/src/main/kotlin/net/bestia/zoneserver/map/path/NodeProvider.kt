package net.bestia.zoneserver.map.path

/**
 * Provider for the pathfinder algorithms with nodes from different sources.
 *
 * @param <T> The type for the nodes to be supplied.
 * @author Thomas Felix
</T> */
interface NodeProvider<T> {

  /**
   * Returns all reachable nodes from the given node. The returned nodes must
   * be provided with a associated walk cost.
   *
   * @param node The node to get the neighbouring and reachable nodes from.
   * @return A set of all reachable nodes with walk cost set.
   */
  fun getConnectedNodes(node: Node<T>): Set<Node<T>>
}
