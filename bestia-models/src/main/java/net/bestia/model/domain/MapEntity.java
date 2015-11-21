package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * This domain model saves the serialized map entities in order to keep the map
 * state between a server shutdown and restart.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Entity
@Table(name = "map_entities")
public class MapEntity implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	private int id;

	@Column(nullable = false)
	private String zoneName;

	@Column(nullable = false)
	private String data;

	public MapEntity() {

	}

	/**
	 * Ctor helps to fast create the entities.
	 * 
	 * @param zoneName
	 *            The zone name of this entity.
	 * @param data
	 *            The serialized data.
	 */
	public MapEntity(String zoneName, String data) {
		if (zoneName == null || zoneName.isEmpty()) {
			throw new IllegalArgumentException("ZoneName can not be null or empty.");
		}

		if (data == null || data.isEmpty()) {
			throw new IllegalArgumentException("Data can not be null or empty.");
		}

		this.zoneName = zoneName;
		this.data = data;
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
		this.zoneName = zoneName;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}
}
