package net.bestia.zoneserver.actor;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.TypedActor;
import net.bestia.messages.JsonMessage;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.zone.SendClientActor;

public class ZoneAkkaApiActor implements ZoneAkkaApi {

	private final ActorSelection sendClientActor;
	private final ActorContext context;

	public ZoneAkkaApiActor() {

		this.context = TypedActor.context();
		this.sendClientActor = context.actorSelection(AkkaCluster.getNodeName(SendClientActor.NAME));
	}

	@Override
	public void sendToClient(JsonMessage message) {
		sendClientActor.tell(sendClientActor, ActorRef.noSender());
	}

}
