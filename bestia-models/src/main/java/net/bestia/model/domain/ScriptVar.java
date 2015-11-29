package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
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
//@Entity
@Table(name = "script_vars", indexes = { @Index(name = "entity_id_key", columnList = "entity_id", unique = false) })
public class ScriptVar implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	private long id;

	@OneToMany(fetch = FetchType.LAZY)
	@JoinColumn(nullable = true)
	private Account account;

	@OneToMany(fetch = FetchType.LAZY)
	@JoinColumn(nullable = true)
	private PlayerBestia playerBestia;

	@Column(name = "entity_id", nullable = false)
	private String entityId;
	
	private String data;
	
	public ScriptVar() {
		
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public Account getAccount() {
		return account;
	}

	public void setAccount(Account account) {
		this.account = account;
	}

	public PlayerBestia getPlayerBestia() {
		return playerBestia;
	}

	public void setPlayerBestia(PlayerBestia playerBestia) {
		this.playerBestia = playerBestia;
	}

	public String getEntityId() {
		return entityId;
	}

	public void setEntityId(String entityId) {
		this.entityId = entityId;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}
}
