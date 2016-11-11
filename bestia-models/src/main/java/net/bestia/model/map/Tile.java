package net.bestia.model.map;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single tile of the bestia game system. All needed information to
 * localize this tile and to find the corresponding {@link TileProperties} with
 * the GID.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Tile implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("gid")
	private final int gid;

	public Tile( int gid) {

		this.gid = gid;
	}

	public int getGid() {
		return gid;
	}
}
