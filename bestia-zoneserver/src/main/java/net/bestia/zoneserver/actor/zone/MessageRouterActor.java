package net.bestia.zoneserver.actor.zone;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import net.bestia.messages.EntityMessage;
import net.bestia.messages.JsonMessage;

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
	
	private ActorRef entities;
	private ActorRef clients;

	@Autowired
	public MessageRouterActor(ActorRef shardEntities, ActorRef shardClients) {

		this.entities = Objects.requireNonNull(shardEntities);
		this.clients = Objects.requireNonNull(shardClients);
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
