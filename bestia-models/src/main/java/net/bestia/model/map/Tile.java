package net.bestia.model.map;

import java.io.Serializable;
import java.util.Objects;

import net.bestia.model.shape.Point;

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

	private final Point position;
	private final int gid;
	private final int layer;

	public Tile(int layer, Point pos, int gid) {

		this.position = Objects.requireNonNull(pos);
		this.layer = layer;
		this.gid = gid;
	}

	public Point getPoint() {
		return position;
	}

	public int getGid() {
		return gid;
	}

	public int getLayer() {
		return layer;
	}
}
