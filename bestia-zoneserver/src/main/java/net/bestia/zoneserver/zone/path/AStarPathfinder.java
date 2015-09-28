package net.bestia.zoneserver.zone.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Uses the A* pathfinding algorithm to find a path.
 * 
 * @author Thomas Felix <thomas.felix>
 *
 */
class AStarPathfinder implements Pathfinder {

	// Max. number of thils
	private final static int MAX_ITERATIONS = 400;

	/**
	 * Node to hold specific information about a A* path.
	 */
	private class Node implements Comparable<Node> {

		public final Node parent;
		public final Vector2 p;

		public final double g; // g is distance from the source
		public final double h; // h is the heuristic of destination.
		public final double f; // f = g + h

		public Node(Node parent, Vector2 p, Vector2 dest) {
			this.parent = parent;
			this.p = p;

			g = calcG();
			h = Math.sqrt((dest.x - p.x) * (dest.x - p.x) + (dest.y - p.y) * (dest.y - p.y));

			f = g + h;
		}

		@Override
		public String toString() {
			return p.toString();
		}

		/**
		 * Sums up all the costs to the origin.
		 * 
		 * @return Summed up way costs of all parents up to the origin.
		 */
		private double calcG() {
			double cost = 0;
			Node nextParent = parent;
			while (nextParent != null) {
				cost += nextParent.g;
				nextParent = nextParent.parent;
			}
			return cost;
		}

		@Override
		public int compareTo(Node o) {
			if (f == o.f) {
				return 0;
			}

			if (f > o.f) {
				return 1;
			} else {
				return -1;
			}
		}

		@Override
		public int hashCode() {
			return p.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Node other = (Node) obj;
			if (p == null) {
				if (other.p != null)
					return false;
			} else if (!p.equals(other.p))
				return false;
			return true;
		}
	}

	private final Map map;
	private final PriorityQueue<Node> openQueue = new PriorityQueue<>();
	private final Set<Node> closedQueue = new HashSet<>();

	/**
	 * Ctor.
	 * 
	 * @param map
	 *            The map to search the paths on.
	 */
	public AStarPathfinder(Map map) {
		this.map = map;
	}

	/**
	 * Adds all connected and walkable nodes adjacent to a node to the open
	 * queue.
	 * 
	 * @param n
	 */
	private void addAdjacentNodesToQueue(Node n, Vector2 dest) {
		final Vector2 last = n.p;
		for (int i = -1; i < 2; i++) {
			for (int j = -1; j < 2; j++) {
				if (i == 0 && j == 0) {
					continue;
				}

				final Vector2 newP = new Vector2(last.x + i, last.y + j);

				if (newP.x < 0 || newP.y < 0) {
					continue;
				}

				if (!map.isWalkable(newP)) {
					continue;
				}

				// Do the special case checks when walking diagonal. Then at
				// lest the upper or lower tile must be free aswell.
				if (i == -1 && j == -1) {
					// Top left.
					if (!map.isWalkable(new Vector2(newP.x, newP.y - 1))
							&& !map.isWalkable(new Vector2(newP.x - 1, newP.y))) {
						continue;
					}
				} else if (i == 1 && j == -1) {
					// top right
					if (!map.isWalkable(new Vector2(newP.x, newP.y - 1))
							&& !map.isWalkable(new Vector2(newP.x + 1, newP.y))) {
						continue;
					}
				} else if (i == -1 && j == 1) {
					// bottom left
					if (!map.isWalkable(new Vector2(newP.x - 1, newP.y))
							&& !map.isWalkable(new Vector2(newP.x, newP.y + 1))) {
						continue;
					}
				} else if (i == 1 && j == 1) {
					// bottom right.
					if (!map.isWalkable(new Vector2(newP.x + 1, newP.y))
							&& !map.isWalkable(new Vector2(newP.x, newP.y + 1))) {
						continue;
					}
				}

				openQueue.add(new Node(n, newP, dest));
			}
		}
	}

	@Override
	public List<Vector2> findPath(Vector2 start, Vector2 end) {
		// Clear all queues.
		openQueue.clear();
		closedQueue.clear();

		// Trivial checks.
		if (!map.isWalkable(start) || !map.isWalkable(end)) {
			return null;
		}

		// Start with the search. Create start node.
		final Node startNode = new Node(null, start, end);

		closedQueue.add(startNode);
		addAdjacentNodesToQueue(startNode, end);

		int i = 0;
		while (i++ < MAX_ITERATIONS) {
			// Get the most promising node.
			final Node nextNode = openQueue.poll();

			// Abort checks.
			if (nextNode == null) {
				// Path could not be found.
				return null;
			}

			if (nextNode.p.equals(end)) {
				// Solution found. Generate path and return.
				return generatePath(nextNode);
			}

			addAdjacentNodesToQueue(nextNode, end);
		}
		// Search exhausted. No solution found.
		return null;
	}

	private List<Vector2> generatePath(Node n) {
		final List<Vector2> path = new ArrayList<>();
		while (n != null) {
			path.add(n.p);
			n = n.parent;
		}
		Collections.reverse(path);
		return path;
	}

}
