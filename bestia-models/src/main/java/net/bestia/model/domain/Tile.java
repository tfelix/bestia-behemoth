package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import net.bestia.model.map.TileProperties;

/**
 * Represents a single tile of the bestia game system. All needed information to
 * localize this tile and to find the corresponding {@link TileProperties} with
 * the GID.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Entity
@Table(name = "tiles")
public class Tile implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private int gid;
	private long x;
	private long y;
	private int layer;

	public Tile() {
		// empty ctor.
	}

	public Tile(int gid, long x, long y, int layer) {
		if (x < 0 || y < 0) {
			throw new IllegalArgumentException("X and Y must be positve or 0.");
		}

		this.gid = gid;
		this.x = x;
		this.y = y;
		this.layer = layer;
	}

	public int getGid() {
		return gid;
	}

	public long getX() {
		return x;
	}

	public long getY() {
		return y;
	}

	public int getLayer() {
		return layer;
	}

	@Override
	public String toString() {
		return String.format("Tile[x: %d, y: %d,  gid: %d, layer: %d]", x, y, gid, layer);
	}
}
