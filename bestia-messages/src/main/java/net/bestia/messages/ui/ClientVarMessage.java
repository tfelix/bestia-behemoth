package net.bestia.messages.ui;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;


public class ClientVarMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;

	private static final String MESSAGE_ID = "cvar";

	@JsonProperty("uuid")
	private String uuid;
	
	@JsonProperty("d")
	private String data;

	protected ClientVarMessage() {
		// no op.
	}
	
	public ClientVarMessage(long accId, String uuid, String data) {
		super(accId);
		
		this.uuid = Objects.requireNonNull(uuid);
		this.data = Objects.requireNonNull(data);
	}

	public String getUuid() {
		return uuid;
	}
	
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
