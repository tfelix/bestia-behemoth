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

	private final T self;
	private Node<T> parent;

	private float ownCost = 0;
	private float completeCost = Float.NaN;

	public Node(T self) {

		this.self = Objects.requireNonNull(self);
	}

	/**
	 * Sets the own cost of this node.
	 * 
	 * @param cost
	 *            The own cost.
	 */
	public void setOwnCost(float cost) {
		this.ownCost = cost;
	}

	/**
	 * Walking cost of this current node.
	 * 
	 * @return
	 */
	float getNodeCost() {

		if (!Float.isNaN(completeCost)) {
			return completeCost;
		}

		if (parent == null) {
			return ownCost;
		} else {
			completeCost = ownCost + parent.getNodeCost();
			return completeCost;
		}
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
