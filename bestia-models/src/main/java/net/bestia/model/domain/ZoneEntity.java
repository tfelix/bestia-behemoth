package net.bestia.model.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 * This domain model saves the serialized map entities in order to keep the map
 * state between a server shutdown and restart.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Entity
@Table(name = "zone_entities", indexes = { @Index(unique = true, columnList = "zoneName") })
public class ZoneEntity implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	private int id;

	@Column(nullable = false)
	private String zoneName;

	@Column(nullable = false)
	private byte[] data;

	public ZoneEntity() {

	}

	/**
	 * Ctor helps to fast create the entities.
	 * 
	 * @param zoneName
	 *            The zone name of this entity.
	 * @param data
	 *            The serialized data.
	 */
	public ZoneEntity(String zoneName, byte[] data) {
		if (zoneName == null || zoneName.isEmpty()) {
			throw new IllegalArgumentException("ZoneName can not be null or empty.");
		}


		this.zoneName = zoneName;
		this.data = Objects.requireNonNull(data, "Data can not be null.");
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getZoneName() {
		return zoneName;
	}

	public void setZoneName(String zoneName) {
		if (zoneName == null || zoneName.isEmpty()) {
			throw new IllegalArgumentException("ZoneName can not be null or empty.");
		}
		this.zoneName = zoneName;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = Objects.requireNonNull(data, "Data can not be null.");
	}
}
