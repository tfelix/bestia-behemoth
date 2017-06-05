package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.Table;

import net.bestia.model.map.MapDataDTO;

/**
 * The {@link MapData} is raw map file data which is used by the map service in
 * order to query and generate the player map data. It lies in the form a binary
 * compressed data to support the huge bestia maps.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Entity
@Table(name = "map_data", indexes = {
		@Index(columnList = "x", name = "x_idx"),
		@Index(columnList = "y", name = "y_idx") })
@IdClass(MapData.MapDataPK.class)
public class MapData {

	/**
	 * Composite primary key helper class.
	 *
	 */
	static class MapDataPK implements Serializable {

		private static final long serialVersionUID = 1L;

		private long x;
		private long y;
		private long width;
		private long height;

		public MapDataPK() {
			// no op.
		}

		public MapDataPK(long x, long y, long width, long height) {

			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (int) (height ^ (height >>> 32));
			result = prime * result + (int) (width ^ (width >>> 32));
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
			MapData other = (MapData) obj;
			if (height != other.height)
				return false;
			if (width != other.width)
				return false;
			if (x != other.x)
				return false;
			if (y != other.y)
				return false;
			return true;
		}
	}

	@Id
	private long x;

	@Id
	private long y;

	@Id
	private long width;

	@Id
	private long height;

	/**
	 * This data storages contain {@link MapDataDTO}s which are encoded map
	 * data.
	 */
	@Lob
	@Column(nullable = false, length = 50000)
	private byte[] data;

	public long getX() {
		return x;
	}

	public void setX(long x) {
		this.x = x;
	}

	public long getY() {
		return y;
	}

	public void setY(long y) {
		this.y = y;
	}

	public long getWidth() {
		return width;
	}

	public void setWidth(long width) {
		this.width = width;
	}

	public long getHeight() {
		return height;
	}

	public void setHeight(long height) {
		this.height = height;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	@Override
	public String toString() {
		return String.format("MapData[x: %d, y: %d, w: %d, h: %d, data: %d bytes]", getX(), getY(), getWidth(),
				getHeight(), data.length);
	}
}
