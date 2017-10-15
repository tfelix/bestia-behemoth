package net.bestia.zoneserver.actor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.messages.internal.entity.EntityEnvelope;

public class AkkaMessageApi implements ZoneMessageApi {
	
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
	public void sendToEntity(long entityId, Object msg) {
		LOG.debug("sendToEntity: id: {}, msg: {}", entityId, msg);
		
		final EntityEnvelope env = new EntityEnvelope(entityId, msg);
		msgRouter.tell(env, ActorRef.noSender());
	}

	/**
	 * Sets the entry point of all message routing into the bestia system. Must
	 * be set before the other methods can be invoked.
	 * 
	 * @param msgRouter
	 *            The actor ref to the messaging actor.
	 */
	public void setMessageEntry(ActorRef msgRouter) {
		this.msgRouter = msgRouter;
	}
}
