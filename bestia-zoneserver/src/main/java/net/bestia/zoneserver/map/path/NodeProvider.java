package net.bestia.zoneserver.map.path;

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

	Set<Node<T>> getConnectedNodes(Node<T> node);

}
