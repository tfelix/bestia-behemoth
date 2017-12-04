package net.bestia.messages.component;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;

public class PlayerComponentMessage extends EntityJsonMessage {
	
	public static final String MESSAGE_ID = "comp.player";

	private static final long serialVersionUID = 1L;
	
	@JsonProperty("pbid")
	private int playerBestiaId;
	
	@JsonProperty("dbn")
	private String databaseName;
	
	@JsonProperty("cn")
	private String customName;

	public PlayerComponentMessage(long accId, long entityId) {
		super(accId, entityId);
		// no op.
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public JsonMessage createNewInstance(long accountId) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public String toString() {
		return String.format("PlayerComponentMessage[]");
	}

}
