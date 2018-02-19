package bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class StatusEffect implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	private int id;

	private String datebaseName;

	/**
	 * Flag if the status is transmitted towards a client.
	 */
	private boolean isClientVisible;

	public int getId() {
		return id;
	}

	public String getDatebaseName() {
		return datebaseName;
	}

	public void setDatebaseName(String datebaseName) {
		this.datebaseName = datebaseName;
	}

	/**
	 * Flag if the status effect is transmitted to the client and shown inside
	 * the client GUI.
	 * 
	 * @return TRUE if the status effect should be visible to the client.
	 */
	public boolean isClientVisible() {
		return isClientVisible;
	}

}
