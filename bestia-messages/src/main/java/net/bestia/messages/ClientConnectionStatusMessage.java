package net.bestia.messages;

import java.util.Objects;

import akka.actor.ActorRef;

public class ClientConnectionStatusMessage extends AccountMessage {

	private static final long serialVersionUID = 1L;

	public enum ConnectionState {
		CONNECTED,
		DISCONNECTED
	}
	
	private final ConnectionState state;
	private final ActorRef webserverRef;
	
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

	@Override
	public String getMessagePath() {
		// TODO Auto-generated method stub
		return null;
	}

}
