package net.bestia.zoneserver.map.path;

import java.util.ArrayList;
import java.util.List;

import net.bestia.model.geometry.Point;

/**
 * Generic path node implementation. It must give some generic information about
 * a path in oder for the path finder to work correctly. Node objects are used
 * within hash maps and sets and thus should implement the
 * {@link #equals(Object)} and {@link #hashCode()} method.
 * 
 * @author Thomas Felix
 *
 * @param <T>
 */
public class Node {
	
	private Point goal;
	private Point self;
	private Node parent;
	private float cost;
	private final List<Node> connections = new ArrayList<>();
	
	public List<Node> getConnections() {
		return connections;
	}


	/**
	 * Walking cost of this current node.
	 * 
	 * @return
	 */
	float getNodeCost() {
		if(parent == null) {
			return 0;
		} else {
			return cost + parent.getNodeCost();
		}
	}

	/**
	 * Gets the heuristic distance towards the goal from this node on.
	 * 
	 * @return The approximated distance.
	 */
	float getHeuristicDistance() {
		return (float) goal.getDistance(self);
	}

	/**
	 * Returns the wrapped object.
	 * 
	 * @return
	 */
	Point getPoint() {
		return self;
	}
}
