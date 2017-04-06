package net.bestia.model.domain;

import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This holds the shortcuts of items and attacks for a certain bestia.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Entity
@Table(name = "shortcuts")
public class Shortcut {
	
	@Id
	@GeneratedValue
	private int id;
	
	@OneToOne
	@JoinColumn(name = "PLAYER_BESTIA_ID", nullable = false)
	private PlayerBestia playerBestia;
	
	@JsonProperty("sc")
	private String shortcut;

	public PlayerBestia getPlayerBestia() {
		return playerBestia;
	}
	
	public void setPlayerBestia(PlayerBestia playerBestia) {
		
		this.playerBestia = Objects.requireNonNull(playerBestia);
	}
	
	public String getShortcut() {
		return shortcut;
	}
	
	public void setShortcut(String shortcut) {
		this.shortcut = shortcut;
	}
}
