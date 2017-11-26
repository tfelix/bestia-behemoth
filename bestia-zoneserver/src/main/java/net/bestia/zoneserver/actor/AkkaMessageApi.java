package net.bestia.zoneserver.actor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.EntityMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.messages.MessageApi;

public class AkkaMessageApi implements MessageApi {
	
	private final static Logger LOG = LoggerFactory.getLogger(AkkaMessageApi.class);
	
	private ActorRef msgRouter;

	@Override
	public void sendToClient(JsonMessage message) {
		LOG.debug("sendToClient: {}", message);
		msgRouter.tell(message, ActorRef.noSender());
	}

	@Override
	public void sendToActiveClientsInRange(EntityJsonMessage message) {
		LOG.debug("sendToActiveClientsInRange: {}", message);
		msgRouter.tell(message, ActorRef.noSender());
	}

	@Override
	public void sendToEntity(EntityMessage msg) {
		LOG.debug("sendToEntity: {}", msg);
		msgRouter.tell(msg, ActorRef.noSender());
	}
}
