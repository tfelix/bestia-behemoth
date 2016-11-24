package net.bestia.zoneserver.map.path;

import java.util.List;

public interface Node<T> {

	List<Node<T>> getConnections();

	float getStartDistance();

	float getNodeCost();
	
	void setParent(Node<?> lastNode);
	
	Node<T> getParent();

	/**
	 * Gets the heuristic distance towards the goal from this node on.
	 * 
	 * @return The aproximated distance.
	 */
	float getHeuristicDistance();

	T getWrapped();
}
