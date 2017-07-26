package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * This will save script variables to the database. These variables can be used
 * for several purposes:
 * <ul>
 * <li>NPC-Player Bestia Variables</li>
 * <li>NPC-Account Variables</li>
 * <li>NPC-Only Variables</li>
 * <li>Zone Global Variables</li>
 * <li>Server Global Variables</li>
 * </ul>
 * 
 * <p>
 * <b>NPC-Player Bestia</b> These variables will relate to bestias to certain
 * NPCs/Entities.
 * </p>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Entity
@Table(name = "script_vars", indexes = { @Index(name = "name_id_key", columnList = "script_key", unique = false) })
public class ScriptVar implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	private long id;

	@Column(name = "script_key", nullable = false)
	private String scriptKey;

	private String data;

	public ScriptVar() {

	}

	public ScriptVar(String key, String data) {
		if (data == null || data.isEmpty()) {
			throw new IllegalArgumentException("Data can not be null or empty.");
		}

		this.data = data;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getScriptKey() {
		return scriptKey;
	}

	public void setScriptKey(String key) {
		if (key == null || key.isEmpty()) {
			throw new IllegalArgumentException("NameKey can not be set to null or empty.");
		}
		this.scriptKey = key;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		if (data == null || data.isEmpty()) {
			throw new IllegalArgumentException("Data can not be set to null or empty. Delte the scriptvar instead.");
		}
		this.data = data;
	}

	@Override
	public String toString() {
		return String.format("SVar[id: %d, key: %s, data: %s]", getId(), getScriptKey(), getData());
	}
}
