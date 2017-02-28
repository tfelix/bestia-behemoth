package net.bestia.messages.login;

import net.bestia.messages.JsonMessage;

/**
 * This message is issued by the engine if it has loaded everything and is able
 * to receive a full update of all entities in range. When the server receives
 * this it will send a heads up of all entities in range and ginves some hints
 * for preloading animation data to the engine.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EngineReadyMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "system.clientready";

	protected EngineReadyMessage() {
		// no op.
	}

	public EngineReadyMessage(long accountId) {
		super(accountId);
		// no op.
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("EngineReadyMessage[accountId: %d]", getAccountId());
	}

	@Override
	public EngineReadyMessage createNewInstance(long accountId) {

		return new EngineReadyMessage(accountId);
	}

}
