package net.bestia.zoneserver.zone.map;

/**
 * Properties of a single tile.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class TileProperties {

	private boolean isWalkable;
	private int walkspeed;

	public boolean isWalkable() {
		return isWalkable;
	}

	public int getWalkspeed() {
		return walkspeed;
	}

}
