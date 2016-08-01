package net.bestia.messages.system;

import java.util.Objects;

import akka.actor.ActorRef;
import net.bestia.messages.AccountMessage;

public class ClientConnectionStatusMessage extends AccountMessage {

	private static final long serialVersionUID = 1L;

	public enum ConnectionState {
		CONNECTED, DISCONNECTED
	}

	private final ConnectionState state;
	private final ActorRef webserverRef;

	/**
	 * TODO Manche klassen müssen von JSON serialisierer ausgenommen werden
	 * können.
	 */
	public ClientConnectionStatusMessage() {
		state = null;
		webserverRef = null;
	}

	public ClientConnectionStatusMessage(ConnectionState state, ActorRef webserverRef) {

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
		// TODO Auto-generated method stub
		return null;
	}
}
