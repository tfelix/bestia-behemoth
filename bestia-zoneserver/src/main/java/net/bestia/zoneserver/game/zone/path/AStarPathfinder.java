package net.bestia.zoneserver.game.zone.path;

import java.util.List;
import java.util.PriorityQueue;

import net.bestia.zoneserver.game.zone.Point;
import net.bestia.zoneserver.game.zone.Zone;


public class AStarPathfinder implements Pathfinder {
	
	private Zone zone;
	
	private class Node implements Comparable<Node> {
		
		public Node parent;
		public Point p;
		//public final double g;  // g is distance from the source
		//public final double h;  // h is the heuristic of destination.
		//public final double f;  // f = g + h 
		
		
		/**
		 * Creates a node via a heuristic for the A* algorithm.
		 * 
		 * @param pos Current position.
		 * @param start Start position.
		 * @param end End position.
		 * @param zone Reference to the zone to get walkspeed.
		 * @return
		 */
	
		/*public static Node createHeuristicNode(Point start, Point pos, Point end, Zone zone) {
			
			return null;
		}*/
		
		@Override
		public int compareTo(Node o) {
			// TODO Auto-generated method stub
			return 0;
		}
	}
	
	private PriorityQueue<Node> openQueue = new PriorityQueue<Node>();
	
	/**
	 * Adds all connected and walkable nodes to the open queue.
	 * 
	 * @param n
	 */
	private void addConnected(Node n) {
		Point last = n.p;
		
		for(int i = -1; i < 2; i++) {
			for(int j = -1; j < 2; j++) {
				Point newP = new Point(last.x - 1, last.y - 1);
				if(i == 0 && j == 0) {
					continue;
				}
				
				if(!zone.isWalkable(newP)) {
					continue;
				}
				
				/*Node newN = Node.createHeuristicNode(start, newP, end, zone);
				openQueue.add(newN);*/
			}
		}
	}

	@Override
	public List<Point> findPath(Point start, Point end, Zone zone) {
		// TODO Auto-generated method stub
		return null;
	}

}
