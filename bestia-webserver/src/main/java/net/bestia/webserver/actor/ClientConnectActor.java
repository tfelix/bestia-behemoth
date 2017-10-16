package net.bestia.webserver.actor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import akka.actor.AbstractActor;
import akka.actor.ActorPath;
import akka.actor.ActorPaths;
import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.cluster.client.ClusterClient;
import akka.cluster.client.ClusterClientSettings;

public class ClientConnectActor extends AbstractActor {

	private final ActorRef uplink;

	public ClientConnectActor() {

		final ClusterClientSettings settings = ClusterClientSettings.create(getContext().getSystem());
		uplink = getContext().actorOf(ClusterClient.props(settings), "uplink");
		
		sendToUplink("FUCK YOU FROM CLIENT");
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Terminated.class, this::handleUplinkClosed)
				.matchAny(this::sendToUplink).build();
	}

	private void handleUplinkClosed(Terminated t) {
		System.err.println("Connection to remote system lost.");
	}
	
	private void sendToUplink(Object msg) {
		uplink.tell(new ClusterClient.Send("/user/ingest", msg, true), getSelf());
	}

}
