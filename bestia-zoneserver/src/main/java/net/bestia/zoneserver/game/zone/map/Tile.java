package net.bestia.zoneserver.game.zone.map;

/**
 * Holds information about a basic tile. Usually this data is loaded from a mapfile first and then stored in RAM. It
 * can, however, be altered on the server during runtime.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Tile {

	private final boolean isWalkable;
	
	/*-
	 * Walkspeed of the entity on this map. It if given in percent with fixed point:
	 * 1000: 100%
	 * 500: 50% etc.
	 */
	private final int walkspeed;

	public Tile(boolean isWalkable, int walkspeed) {

		this.isWalkable = isWalkable;
		this.walkspeed = walkspeed;

	}

	public boolean isWalkable() {
		return isWalkable;
	}

	public int getWalkspeed() {
		return walkspeed;
	}
	
	@Override
	public String toString() {
		return String.format("Tile[walkable: %b, walkSpd: %d]", isWalkable, walkspeed);
	}

}
