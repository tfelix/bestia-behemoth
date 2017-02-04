package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Index;
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
@Table(name = "tiles", indexes = {
		@Index(columnList = "x", name = "x_idx"),
		@Index(columnList = "y", name = "y_idx") })
@IdClass(Tile.TilePK.class)
public class Tile implements Serializable {

	/**
	 * Composite primary key helper class.
	 *
	 */
	static class TilePK implements Serializable {
		
		private static final long serialVersionUID = 1L;
		
		private long x;
		private long y;
		private short layer;

		public TilePK() {
			// no op.
		}

		public TilePK(long x, long y, short layer) {

			this.x = x;
			this.y = y;
			this.layer = layer;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + layer;
			result = prime * result + (int) (x ^ (x >>> 32));
			result = prime * result + (int) (y ^ (y >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TilePK other = (TilePK) obj;
			if (layer != other.layer)
				return false;
			if (x != other.x)
				return false;
			if (y != other.y)
				return false;
			return true;
		}
	}

	private static final long serialVersionUID = 1L;

	private int gid;
	
	@Id
	private long x;
	
	@Id
	private long y;
	
	@Id
	private short layer;

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
		this.layer = (short)layer;
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

	public short getLayer() {
		return layer;
	}

	@Override
	public String toString() {
		return String.format("Tile[x: %d, y: %d,  gid: %d, layer: %d]", x, y, gid, layer);
	}
}
