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
	
	private final ActorRef entities;
	private final ActorRef clients;

	@Autowired
	public MessageRouterActor(ActorRef entities, ActorRef clients) {

		
		this.entities = Objects.requireNonNull(entities);
		this.clients = Objects.requireNonNull(clients);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(EntityMessage.class, this::sendToEntityActor)
				.match(JsonMessage.class, this::sendToClient)
				.match(String.class, s -> System.err.println(s))
				.build();
	}

	private void sendToEntityActor(EntityMessage msg) {
		entities.tell(msg, getSender());
	}

	private void sendToClient(JsonMessage msg) {
		clients.tell(msg, getSender());
	}
}
