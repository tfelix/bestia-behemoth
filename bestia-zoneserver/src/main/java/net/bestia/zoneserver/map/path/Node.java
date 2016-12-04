package net.bestia.zoneserver.map.path;

import java.util.List;

/**
 * Generic path node implementation. It must give some generic information about
 * a path in oder for the path finder to work correctly. Node objects are used
 * within hash maps and sets and thus should implement the
 * {@link #equals(Object)} and {@link #hashCode()} method.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 * @param <T>
 */
public interface Node<T> {

	List<Node<T>> getConnections();

	float getStartDistance();

	/**
	 * Walking cost of this current node.
	 * 
	 * @return
	 */
	float getNodeCost();

	/**
	 * Gets the heuristic distance towards the goal from this node on.
	 * 
	 * @return The aproximated distance.
	 */
	float getHeuristicDistance();

	/**
	 * Returns the wrapped object.
	 * 
	 * @return
	 */
	T getWrapped();
}
