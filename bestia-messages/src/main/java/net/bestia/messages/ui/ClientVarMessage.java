package net.bestia.messages.ui;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.bestia.messages.JsonMessage;

import java.util.Objects;


public class ClientVarMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "cvar";

	private final String uuid;
	private final String data;
	
	public ClientVarMessage() {
		super(0);
		
		this.uuid = null;
		this.data = null;
	}
	
	public ClientVarMessage(long accId, String uuid, String data) {
		super(accId);
		
		this.uuid = Objects.requireNonNull(uuid);
		this.data = Objects.requireNonNull(data);
	}

	@JsonProperty("uuid")
	public String getUuid() {
		return uuid;
	}
	
	@JsonProperty("d")
	public String getData() {
		return data;
	}
	
	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public JsonMessage createNewInstance(long accountId) {
		return new ClientVarMessage(accountId, getUuid(), getData());
	}

}
