package net.bestia.model.domain;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * This is a persisted component which is saved into the database in case the
 * server goes down and the game has to be restored.
 * 
 * @author Thomas Felix
 *
 */
@Entity
@Table(name = "component_data")
public class ComponentData {

	@Id
	private long id;

	private byte[] data;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

}
