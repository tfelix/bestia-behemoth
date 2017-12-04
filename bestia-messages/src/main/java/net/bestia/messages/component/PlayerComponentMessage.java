package net.bestia.messages.component;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This is a special message send if a player component was attached to the
 * entity.
 * 
 * @author Thomas Felix
 *
 */
public class PlayerComponentMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("pbid")
	private final long playerBestiaId;

	@JsonProperty("dbn")
	private final String databaseName;

	@JsonProperty("cn")
	private final String customName;

	public PlayerComponentMessage(long playerBestiaId, String databaseName, String customName) {

		this.playerBestiaId = playerBestiaId;
		this.databaseName = databaseName;
		this.customName = customName;
	}
	
	public long getPlayerBestiaId() {
		return playerBestiaId;
	}
	
	public String getDatabaseName() {
		return databaseName;
	}
	
	public String getCustomName() {
		return customName;
	}

	@Override
	public String toString() {
		return String.format("PlayerComponentMessage[pbid: %d]", getPlayerBestiaId());
	}
}
