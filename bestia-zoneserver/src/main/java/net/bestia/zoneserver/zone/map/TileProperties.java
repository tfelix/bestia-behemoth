package net.bestia.zoneserver.zone.map;

import java.io.Serializable;

/**
 * Properties of a single tile.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class TileProperties implements Serializable {

	private static final long serialVersionUID = 1L;
	private final boolean isWalkable;
	private final int walkspeed;
	
	
	public TileProperties(boolean isWalkable, int walkspeed) {
		this.isWalkable = isWalkable;
		this.walkspeed = walkspeed;
	}

	public boolean isWalkable() {
		return isWalkable;
	}

	public int getWalkspeed() {
		return walkspeed;
	}

}
