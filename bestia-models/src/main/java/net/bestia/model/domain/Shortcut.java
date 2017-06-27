package net.bestia.model.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * This holds the shortcuts of items and attacks for a certain bestia.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Entity
@Table(name = "shortcuts")
public class Shortcut implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * JSON representation of this database object. Can be send to the client.
	 *
	 */
	public static class ShortcutJson implements Serializable {

		private static final long serialVersionUID = 1L;

		@JsonProperty("sid")
		private int shortcutId;

		@JsonProperty("aid")
		private long accountId;

		@JsonProperty("pbid")
		private long playerBestiaId;

		@JsonRawValue
		private String data;

		private ShortcutJson(Shortcut shortcut) {

			this.accountId = shortcut.getAccount().getId();
			this.shortcutId = shortcut.getId();

			if (shortcut.getPlayerBestia() != null) {
				this.playerBestiaId = shortcut.getPlayerBestia().getId();
			}

			this.data = shortcut.getData();
		}
	}

	@Id
	@GeneratedValue
	private int id;

	/**
	 * This is the slot id of the shortcut to provide a ordered display but also
	 * a flexible amount of usable slots. The service makes sure all shortcuts
	 * are given in a valid ways this means no slot id is set twice.
	 */
	private int slotId;

	@OneToOne
	@JoinColumn(name = "PLAYER_BESTIA_ID", nullable = true)
	private PlayerBestia playerBestia;

	@OneToOne
	@JoinColumn(name = "ACCOUNT_ID", nullable = false)
	private Account account;

	/**
	 * Data containing this shortcut.
	 */
	private String data;

	public Shortcut() {
		// no op.
	}
	
	public int getSlotId() {
		return slotId;
	}

	public int getId() {
		return id;
	}

	public String getData() {
		return data;
	}

	public PlayerBestia getPlayerBestia() {
		return playerBestia;
	}

	public void setPlayerBestia(PlayerBestia playerBestia) {

		this.playerBestia = Objects.requireNonNull(playerBestia);
	}

	public Account getAccount() {
		return account;
	}

	public ShortcutJson toJSON() {
		return new ShortcutJson(this);
	}
}
