package bestia.messages.ui;

import com.fasterxml.jackson.annotation.JsonProperty;

import bestia.messages.JsonMessage;

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
	
	private ClientVarRequestMessage() {
		super(0);
		// no op.
	}

	public ClientVarRequestMessage(long accountId) {
		super(accountId);
		// no op.
	}

	public static ClientVarRequestMessage request(long accId, String key, String uuid) {
		final ClientVarRequestMessage msg = new ClientVarRequestMessage(accId);
		msg.key = key;
		msg.uuid = uuid;
		msg.mode = Mode.REQ;
		return msg;
	}

	public static ClientVarRequestMessage delete(long accId, String key) {
		final ClientVarRequestMessage msg = new ClientVarRequestMessage(accId);
		msg.key = key;
		msg.mode = Mode.DEL;
		return msg;
	}

	public static ClientVarRequestMessage set(long accId, String key, String uuid, String data) {
		final ClientVarRequestMessage msg = new ClientVarRequestMessage(accId);
		msg.key = key;
		msg.uuid = uuid;
		msg.data = data;
		msg.mode = Mode.SET;
		return msg;
	}

	public static ClientVarRequestMessage reply(ClientVarRequestMessage request, String data) {

		// Can only reply to requests.
		if (request.getMode() != Mode.REQ) {
			throw new IllegalArgumentException("Can only replay to REQ mode message.");
		}

		final ClientVarRequestMessage msg = new ClientVarRequestMessage(request.getAccountId());
		msg.key = request.getKey();
		msg.uuid = request.getUuid();
		msg.mode = Mode.REQ;
		msg.data = data;

		return msg;
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
