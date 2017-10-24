package net.bestia.messages.internal;

import java.util.Objects;

import akka.actor.ActorRef;
import net.bestia.messages.AccountMessage;

/**
 * This message is send by the webserver frontend as soon as a client is fully
 * connected and must be registered into the bestia system. As soon as this
 * message arrives the client is authenticated and connected and must/can
 * receive messages from now on.
 * 
 * @author Thomas Felix
 *
 */
public class ClientConnectMessage extends AccountMessage {

	private static final long serialVersionUID = 1L;

	public static enum ConnectionState {
		CONNECTED, DISCONNECTED, UNKNOWN
	}

	private final ConnectionState state;
	private final ActorRef webserverRef;

	public ClientConnectMessage(long accId, ConnectionState state, ActorRef webserverRef) {
		super(accId);
		this.state = state;
		this.webserverRef = Objects.requireNonNull(webserverRef);
	}

	public ConnectionState getState() {
		return state;
	}

	/**
	 * @return Webserver who did send this message and to which the client
	 *         mentioned in this message is connected.
	 */
	public ActorRef getWebserverRef() {
		return webserverRef;
	}

	@Override
	public String toString() {
		return String.format("ClientConnectionStatusMessage[accId: %d, status: %s]", getAccountId(), state.toString());
	}

	@Override
	public ClientConnectMessage createNewInstance(long accountId) {
		return new ClientConnectMessage(accountId, this.state, this.webserverRef);
	}
}
