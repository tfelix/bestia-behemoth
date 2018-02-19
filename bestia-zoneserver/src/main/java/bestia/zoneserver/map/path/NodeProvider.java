package bestia.zoneserver.map.path;

import java.util.Set;

/**
 * Provider for the pathfinder algorithms with nodes from different sources.
 * 
 * @author Thomas Felix
 *
 * @param <T>
 *            The type for the nodes to be supplied.
 */
public interface NodeProvider<T> {

	/**
	 * Returns all reachable nodes from the given node. The returned nodes must
	 * be provided with a associated walk cost.
	 * 
	 * @param node
	 *            The node to get the neighbouring and reachable nodes from.
	 * @return A set of all reachable nodes with walk cost set.
	 */
	Set<Node<T>> getConnectedNodes(Node<T> node);

}
