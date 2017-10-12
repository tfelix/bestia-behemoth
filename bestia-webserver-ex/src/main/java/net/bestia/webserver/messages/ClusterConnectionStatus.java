package net.bestia.webserver.messages;

import java.io.Serializable;

import akka.actor.ActorRef;

public class ClusterConnectionStatus implements Serializable {
	
	public enum State {
		CONNECTED,
		DISCONNECTED
	}

	private static final long serialVersionUID = 1L;
	
	private final State state;
	private final ActorRef clusterConnection;
	
	public ClusterConnectionStatus(State state) {
		this(state, null);
	}
	
	public ClusterConnectionStatus(State state, ActorRef clusterConnection) {
		
		this.state = state;
		this.clusterConnection = clusterConnection;
	}
	
	public State getState() {
		return state;
	}
	
	public ActorRef getClusterConnection() {
		return clusterConnection;
	}
}
