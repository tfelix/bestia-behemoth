package net.bestia.messages.internal;

import java.util.Objects;

import akka.actor.ActorRef;
import net.bestia.messages.AccountMessage;

public class ClientConnectionStatusMessage extends AccountMessage {

	private static final long serialVersionUID = 1L;

	public static enum ConnectionState {
		CONNECTED, DISCONNECTED
	}

	private final ConnectionState state;
	private final ActorRef webserverRef;

	/**
	 * Std. ctor (necessairy for Jackson).
	 */
	public ClientConnectionStatusMessage() {
		state = null;
		webserverRef = null;
	}

	public ClientConnectionStatusMessage(long accId, ConnectionState state, ActorRef webserverRef) {
		super(accId);
		this.state = state;
		this.webserverRef = Objects.requireNonNull(webserverRef);
	}

	public ConnectionState getState() {
		return state;
	}

	public ActorRef getWebserverRef() {
		return webserverRef;
	}

	@Override
	public String getMessageId() {
		return String.format("ClientConnectionStatusMessage[status: %s]", state.toString());
	}
}
