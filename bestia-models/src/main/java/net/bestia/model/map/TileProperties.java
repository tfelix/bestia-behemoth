package net.bestia.model.map;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Properties of a single tile.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class TileProperties implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("iw")
	private final boolean isWalkable;

	@JsonProperty("w")
	private final int walkspeed;

	/**
	 * Ctor. Walkspeed is a fixed point two decimal value between 0 and 300.
	 * 
	 * @param isWalkable
	 *            Flag if the tile is walkable at all.
	 * @param walkspeed
	 *            The base walkspeed on this tile.
	 */
	public TileProperties(boolean isWalkable, int walkspeed) {
		if (walkspeed < 0) {
			throw new IllegalArgumentException("Walkspeed must be 0 or positive.");
		}

		this.isWalkable = isWalkable;
		this.walkspeed = walkspeed;
	}

	/**
	 * Flag if this tile is walkable at all.
	 * 
	 * @return TRUE if it is walkable. FALSE otherwise.
	 */
	public boolean isWalkable() {
		return isWalkable;
	}

	/**
	 * The walkspeed on this tile.
	 * 
	 * @return
	 */
	public int getWalkspeed() {
		return walkspeed;
	}
}
