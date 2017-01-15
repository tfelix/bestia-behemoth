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
	
	public TileProperties(boolean isWalkable, int walkspeed) {
		if(walkspeed < 0) {
			throw new IllegalArgumentException("Walkspeed must be 0 or positive.");
		}
		
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
