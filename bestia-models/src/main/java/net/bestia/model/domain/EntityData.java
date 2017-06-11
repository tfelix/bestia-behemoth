package net.bestia.model.domain;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * This is a persisted entity which is saved into the database in case the
 * server goes down and the game has to be restored.
 * 
 * @author Thomas Felix
 *
 */
@Entity
@Table(name = "entity_data")
public class EntityData {

	@Id
	private int id;

	private byte[] data;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

}
