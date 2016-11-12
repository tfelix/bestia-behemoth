package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * The {@link Tileset} holds information about the concrete pack to load. It can
 * be queried for the GID in order to get the apropriate tileset information.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Entity
@Table(name = "tileset", indexes = {
		@Index(columnList = "min_gid"),
		@Index(columnList = "max_gid") })
public class Tileset implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	private int id;

	@Column(unique = true, nullable = false)
	private String name;

	@Column(name = "min_gid")
	private long minGid;

	@Column(name = "max_gid")
	private long maxGid;

	@Override
	public String toString() {
		return String.format("Tileset[id: %d, name: %s, minGid: %d, maxGid: %d]", getId(), getName(), getMinGid(),
				getMaxGid());
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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
}
