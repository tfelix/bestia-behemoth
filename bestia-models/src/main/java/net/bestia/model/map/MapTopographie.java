package net.bestia.model.map;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * This class holds the connections of the map nodes of the bestia game. Will be
 * used by the AI rout planning behavior. It need to have at least a rough
 * coordinate placement in order to generate a distance to the target. The node
 * weight is also given by the map diagonal as an estimation.
 * 
 * @category WORKINPROGRESS
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapTopographie {
	
	public class TopographyNode {
		private int weight;
		private int x;
		private int y;
		
		private Set<TopographyNode> connections = new HashSet<>();
		
		@Override
		public int hashCode() {
			return Objects.hash(weight, x, y);
		}
		
		@Override
		public String toString() {
			return String.format("TopoNode[weight: %d, x: %d, y: %d, connections: %s]", weight, x, y, connections.toString());
		}
	}

}
