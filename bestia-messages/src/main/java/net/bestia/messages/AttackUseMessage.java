package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A message from the client to the server to use an attack.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackUseMessage extends InputMessage {

	@JsonIgnore
	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "attack.use";

	@JsonProperty("aid")
	private int attackId;

	private int x;
	private int y;

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getZoneMessagePath();
	}

	@Override
	public String toString() {
		return String.format("AttackUseMessage[accId: {}, bestiaId: {}, attackId: {}, x: {}, y: {}]", getAccountId(),
				getPlayerBestiaId(), attackId, x, y);
	}
}
