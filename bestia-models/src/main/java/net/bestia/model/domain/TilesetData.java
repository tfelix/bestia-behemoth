package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * The {@link TilesetData} holds information about the concrete pack to load. It
 * can be queried for the GID in order to get the apropriate tileset
 * information.
 * 
 * @author Thomas Felix
 *
 */
@Entity
@Table(name = "tileset", indexes = {
		@Index(columnList = "min_gid"),
		@Index(columnList = "max_gid") })
public class TilesetData implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	private int id;

	@Column(name = "min_gid")
	private long minGid;

	@Column(name = "max_gid")
	private long maxGid;

	/**
	 * JSON serialized description of the tileset.
	 */
	private String data;

	@Override
	public String toString() {
		return String.format("Tileset[id: %d, minGid: %d, maxGid: %d]", getId(), getMinGid(),
				getMaxGid());
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public long getMinGid() {
		return minGid;
	}

	public void setMinGid(long minGid) {
		this.minGid = minGid;
	}

	public long getMaxGid() {
		return maxGid;
	}

	public void setMaxGid(long maxGid) {
		this.maxGid = maxGid;
	}

	public String getData() {
		return data;
	}
}
