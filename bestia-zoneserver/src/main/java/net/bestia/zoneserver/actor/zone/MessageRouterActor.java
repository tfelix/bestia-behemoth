package net.bestia.zoneserver.actor.zone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.sharding.ClusterSharding;
import net.bestia.messages.EntityMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.server.EntryActorNames;

/**
 * Central message control hub. Incoming messages are deliverd to clients or
 * bestia subcomponents.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class MessageRouterActor extends AbstractActor {
	
	public static final String NAME = "messageRouter";
	
	private final ActorRef entities;
	private final ActorRef clients;

	@Autowired
	public MessageRouterActor() {

		final ClusterSharding sharding = ClusterSharding.get(getContext().getSystem());
		this.entities = sharding.shardRegion(EntryActorNames.SHARD_ENTITY);
		this.clients = sharding.shardRegion(EntryActorNames.SHARD_CONNECTION);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(EntityMessage.class, this::sendToEntityActor)
				.match(JsonMessage.class, this::sendToClient)
				.build();
	}

	private void sendToEntityActor(Object msg) {
		entities.tell(msg, getSender());
	}

	private void sendToClient(Object msg) {
		clients.tell(msg, getSender());
	}
}
