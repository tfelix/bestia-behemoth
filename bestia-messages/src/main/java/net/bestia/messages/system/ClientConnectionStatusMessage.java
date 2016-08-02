package net.bestia.messages.system;

import java.util.Objects;

import akka.actor.ActorRef;
import net.bestia.messages.Message;

public class ClientConnectionStatusMessage extends Message {

	private static final long serialVersionUID = 1L;

	public enum ConnectionState {
		CONNECTED, DISCONNECTED
	}

	private final long accountId;
	private final ConnectionState state;
	private final ActorRef webserverRef;

	/**
	 * TODO Manche klassen müssen von JSON serialisierer ausgenommen werden
	 * können.
	 */
	public ClientConnectionStatusMessage() {
		state = null;
		webserverRef = null;
		accountId = 0;
	}

	public ClientConnectionStatusMessage(long accId, ConnectionState state, ActorRef webserverRef) {
		this.accountId = accId;
		this.state = state;
		this.webserverRef = Objects.requireNonNull(webserverRef);
	}

	public ConnectionState getState() {
		return state;
	}

	public ActorRef getWebserverRef() {
		return webserverRef;
	}
	
	public long getAccountId() {
		return accountId;
	}

	@Override
	public String getMessageId() {
		// TODO Auto-generated method stub
		return null;
	}
}
