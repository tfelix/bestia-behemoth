package net.bestia.zoneserver.map.path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

public class AStarPathfinder implements Pathfinder {

	private final PriorityQueue<Node<?>> openSet;
	private final Set<Node<?>> closedSet = new HashSet<>();

	public AStarPathfinder() {
		this.openSet = new PriorityQueue<>(50, new Comparator<Node<?>>() {
			@Override
			public int compare(Node<?> a, Node<?> b) {
				
				float dA = a.getNodeCost() + a.getStartDistance();
				float dB = b.getNodeCost() + b.getStartDistance();
				
				if (Math.abs(dA-dB) < 0.00001f) {
					return 0;
				}
				
				return (dA < dB) ? -1 : 1; 
			}	
		});
	}

	@Override
	public List<Node<?>> findPath(Node<?> start, Node<?> end) {
		
		// Beginning from the start, add all neighbour nodes
		openSet.addAll(start.getConnections());
		
		Node<?> lastNode = null;
		Node<?> currentNode = null;
		while(!openSet.isEmpty()) {
			lastNode = currentNode;
			currentNode = openSet.remove();
			currentNode.setParent(lastNode);
			closedSet.add(currentNode);
			
			// Check if we found the solution and reconstruct path.
			if(currentNode.equals(end)) {
				final List<Node<?>> solution = new ArrayList<>();
	
				while(currentNode != null) {
					solution.add(currentNode);
					currentNode = currentNode.getParent();
				}
				return solution;
			}
			
			currentNode.getConnections().forEach(neighbour -> {
				if(closedSet.contains(neighbour)) {
					return;
				}
				openSet.add(neighbour);
			});
		}
		
		return null;
	}

}