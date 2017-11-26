package net.bestia.zoneserver.actor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.EntityMessage;
import net.bestia.messages.JsonMessage;

public class AkkaMessageApi implements ZoneMessageApi {
	
	private final static Logger LOG = LoggerFactory.getLogger(AkkaMessageApi.class);

	private ActorRef sendToClientActor;
	private ActorRef sendToActiveActor;
	private ActorRef sendToEntityActor;

	@Override
	public void sendToClient(JsonMessage message) {
		LOG.debug("sendToClient: {}", message);
		sendToClientActor.tell(message, ActorRef.noSender());
	}

	@Override
	public void sendToActiveClientsInRange(EntityJsonMessage message) {
		LOG.debug("sendToActiveClientsInRange: {}", message);
		sendToActiveActor.tell(message, ActorRef.noSender());
	}

	@Override
	public void sendToEntity(EntityMessage msg) {
		LOG.debug("sendToEntity: {}", msg);
		sendToEntityActor.tell(msg, ActorRef.noSender());
	}

	@Override
	public void setReceivingActor(ActorRef singleClient, ActorRef allClientsRange, ActorRef entity) {
		
		this.sendToClientActor = singleClient;
		this.sendToActiveActor = allClientsRange;
		this.sendToEntityActor = entity;
	}
}
