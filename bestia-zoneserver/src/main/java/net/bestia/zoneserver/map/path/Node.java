package net.bestia.zoneserver.map.path;

import java.util.Objects;

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
public class Node<T> {

	private T goal;
	private final T self;
	private Node<T> parent;
	private float cost = Float.NaN;

	public Node(T self) {

		this.self = Objects.requireNonNull(self);
	}

	/**
	 * Walking cost of this current node.
	 * 
	 * @return
	 */
	float getNodeCost() {

		if (!Float.isNaN(cost)) {
			return cost;
		}

		if (parent == null) {
			return 0;
		} else {
			cost = cost + parent.getNodeCost();
			return cost;
		}
	}

	/**
	 * Gets the heuristic distance towards the goal from this node on.
	 * 
	 * @return The approximated distance.
	 */
	float getHeuristicDistance(HeuristicEstimator<T> estimator) {

		return estimator.getDistance(self, goal);

	}

	/**
	 * Returns the wrapped object.
	 * 
	 * @return The wrapped object.
	 */
	T getSelf() {
		return self;
	}

	/**
	 * Returns the wrapped point object.
	 * 
	 * @return
	 */
	T getLocation() {
		return self;
	}

	/**
	 * Sets the parent. Can be null. This means the node is the starting node.
	 * 
	 * @param parent
	 *            The parent of this node.
	 */
	void setParent(Node<T> parent) {
		this.parent = parent;
	}

	/**
	 * The parent of the node. Can be null.
	 * 
	 * @return The parent or NULL.
	 */
	Node<T> getParent() {
		return parent;
	}

	@Override
	public boolean equals(Object obj) {
		return self.equals(obj);
	}

	@Override
	public int hashCode() {
		return self.hashCode();
	}

	@Override
	public String toString() {
		return String.format("Node[%s]", getSelf().toString());
	}
}
