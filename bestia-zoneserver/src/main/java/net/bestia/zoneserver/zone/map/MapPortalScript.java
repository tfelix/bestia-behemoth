package net.bestia.zoneserver.zone.map;

import net.bestia.model.domain.Location;
import net.bestia.zoneserver.zone.shape.CollisionShape;

public class MapPortalScript {
	
	private final Location destination;
	private final CollisionShape shape;
	
	public MapPortalScript(Location dest, CollisionShape shape) {
		this.destination = dest;
		this.shape = shape;
	}
	
	public Location getDestination() {
		return destination;
	}
	
	public CollisionShape getShape() {
		return shape;
	}
	
}