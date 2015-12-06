package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

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
@Table(name = "script_vars",
		uniqueConstraints = {
			@UniqueConstraint(columnNames = { "id", "ACCOUNT", "name_id" }), 
			@UniqueConstraint(columnNames = { "id", "ACCOUNT", "PLAYER_BESTIA", "name_id" })
		},
		indexes = { @Index(name = "name_id_key", columnList = "name_id", unique = false) })
public class ScriptVar implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	private long id;

	@ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@JoinColumn(nullable = true, name="ACCOUNT")
	private Account account;

	@ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@JoinColumn(nullable = true, name="PLAYER_BESTIA")
	private PlayerBestia playerBestia;

	@Column(name = "name_id", nullable = false)
	private String nameId;

	private String data;

	public ScriptVar() {

	}

	public ScriptVar(String key, String data, Account acc) {
		if (data == null || data.isEmpty()) {
			throw new IllegalArgumentException("Data can not be null or empty.");
		}
		if (acc == null) {
			throw new IllegalArgumentException("Account can not be null.");
		}

		this.data = data;
		this.account = acc;
	}

	public ScriptVar(String key, String data, Account acc, PlayerBestia playerBestia) {
		if (data == null || data.isEmpty()) {
			throw new IllegalArgumentException("Data can not be null or empty.");
		}
		if (acc == null) {
			throw new IllegalArgumentException("Account can not be null.");
		}

		if (playerBestia == null) {
			throw new IllegalArgumentException("PlayerBestia can not be null.");
		}

		this.data = data;
		this.account = acc;
		this.playerBestia = playerBestia;
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
		if (account == null) {
			throw new IllegalArgumentException("Account can not be set to null.");
		}

		this.account = account;
	}

	public PlayerBestia getPlayerBestia() {
		return playerBestia;
	}

	public void setPlayerBestia(PlayerBestia playerBestia) {
		this.playerBestia = playerBestia;
	}

	public String getNameId() {
		return nameId;
	}

	public void setNameKey(String key) {
		if (key == null || key.isEmpty()) {
			throw new IllegalArgumentException("NameKey can not be set to null or empty.");
		}
		this.nameId = key;
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
}
