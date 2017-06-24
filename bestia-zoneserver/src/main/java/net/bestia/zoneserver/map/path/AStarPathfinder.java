package net.bestia.zoneserver.map.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the A* pathfinding algorithm.
 * 
 * @author Thomas Felix
 *
 */
public class AStarPathfinder<T> implements Pathfinder<T> {

	private static final Logger LOG = LoggerFactory.getLogger(AStarPathfinder.class);

	private PriorityQueue<Node<T>> openSet;
	private final Set<Node<T>> closedSet = new HashSet<>();

	private final NodeProvider<T> nodeProvider;
	private final HeuristicEstimator<T> estimator;

	/**
	 * Maximum number of iterations in order to prevent extensive search on big
	 * maps.
	 */
	private final int maxIteration;

	/**
	 * Alias to {@link #AStarPathfinder(NodeProvider, HeuristicEstimator, int)}
	 * with default iterations set to 1000.
	 * 
	 * @param nodeProvider
	 *            A node provider.
	 * @param estimator
	 *            An estimator.
	 */
	public AStarPathfinder(NodeProvider<T> nodeProvider, HeuristicEstimator<T> estimator) {
		this(nodeProvider, estimator, 1000);
	}

	public AStarPathfinder(NodeProvider<T> nodeProvider, HeuristicEstimator<T> estimator, int maxIterations) {

		if (maxIterations <= 0) {
			throw new IllegalArgumentException("MaxIterations must be greater then 0.");
		}

		this.nodeProvider = Objects.requireNonNull(nodeProvider);
		this.estimator = Objects.requireNonNull(estimator);
		this.maxIteration = maxIterations;

	}

	@Override
	public List<Node<T>> findPath(Node<T> start, Node<T> end) {

		LOG.trace("Finding path from {} to {}.", start, end);

		this.openSet = new PriorityQueue<>(50, new Comparator<Node<T>>() {
			@Override
			public int compare(Node<T> a, Node<T> b) {

				final float targetDistA = estimator.getDistance(a.getSelf(), end.getSelf());
				final float targetDistB = estimator.getDistance(a.getSelf(), end.getSelf());

				float dA = a.getNodeCost() + targetDistA;
				float dB = b.getNodeCost() + targetDistB;

				// Tie breaker.
				if (Math.abs(dA - dB) < 0.00001f) {
					return 0;
				}

				return (dA < dB) ? -1 : 1;
			}
		});

		Node<T> lastNode = null;
		Node<T> currentNode = start;

		int i = 0;
		while (!openSet.isEmpty() && ++i <= maxIteration) {

			// Beginning from the start, add all neighboring nodes to open set.
			final Set<Node<T>> connections = nodeProvider.getConnectedNodes(currentNode);
			LOG.trace("Node {} connections; {}.", currentNode, connections);
			connections.stream().filter(c -> !closedSet.contains(c)).forEach(openSet::add);

			lastNode = currentNode;
			currentNode = openSet.remove();
			currentNode.setParent(lastNode);
			closedSet.add(currentNode);

			// Check if we already found the solution.
			// We can reconstruct the path then.
			if (currentNode.equals(end)) {
				final List<Node<T>> solution = new ArrayList<>();

				while (currentNode != null) {
					solution.add(currentNode);
					currentNode = currentNode.getParent();
				}

				// Path is now in reverse order. Fix this.
				Collections.reverse(solution);

				return solution;
			}
		}

		return Collections.emptyList();
	}

}
