package net.bestia.messages.ui;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;

/**
 * Asks the server to replay with a list of shortcuts for the current entity and
 * the account.
 * 
 * @author Thomas Felix
 *
 */
public class ClientVarRequestMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;

	private static final String MESSAGE_ID = "cvar.req";

	/**
	 * Which mode the client the message contains.
	 *
	 */
	public enum Mode {
		REQ, SET, DEL
	}

	@JsonProperty("key")
	private String key;

	@JsonProperty("uuid")
	private String uuid;
	
	@JsonProperty("m")
	private Mode mode;
	
	@JsonProperty("d")
	private String data;

	protected ClientVarRequestMessage() {
		// no op.
	}

	public ClientVarRequestMessage(long accountId) {
		super(accountId);
		// no op.
	}

	/**
	 * The key of the request.
	 * 
	 * @return Key of the request.
	 */
	public String getKey() {
		return key;
	}

	/**
	 * Returns the UUID of the request. The response should be given the same
	 * uuid.
	 * 
	 * @return The uuid of the request.
	 */
	public String getUuid() {
		return uuid;
	}
	
	public String getData() {
		return data;
	}
	
	public Mode getMode() {
		return mode;
	}

	@Override
	public JsonMessage createNewInstance(long accountId) {
		return new ClientVarRequestMessage(getAccountId());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

}