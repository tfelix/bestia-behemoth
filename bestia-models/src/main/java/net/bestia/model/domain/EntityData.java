package net.bestia.model.domain;

import java.io.Serializable;

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
public class EntityData implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	private long id;

	private byte[] data;

	public long getId() {
		return id;
	}

	public void setId(long l) {
		this.id = l;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

}
