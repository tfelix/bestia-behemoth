package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Special class for player inputs which should not generate a server command. Instead the input is directed into the EC
 * system of the zone which is responsible for the given player bestia. Some sanity checks are performed like if the
 * account is really the owner of the bestia wished to control. Other than that the ECS is responsible for performing
 * the actions.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class InputMessage extends Message {

	@JsonIgnore
	private static final long serialVersionUID = 1L;

	@JsonProperty("pbid")
	private int playerBestiaId = 0;

	public InputMessage() {

	}

	public InputMessage(Message msg, int pbid) {
		super(msg);
		setPlayerBestiaId(pbid);
	}

	/**
	 * Gets the player bestia id for which this message is meant. Not all messages are subject to a selected player
	 * bestia thus this field can contain the ID 0 which is reserved for this case.
	 * 
	 * @return
	 */
	@JsonIgnore
	public int getPlayerBestiaId() {
		return playerBestiaId;
	}

	public void setPlayerBestiaId(int playerBestiaId) {
		this.playerBestiaId = playerBestiaId;
	}

}
