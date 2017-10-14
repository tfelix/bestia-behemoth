package net.bestia.zoneserver.actor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;

public class AkkaMessageApi implements ZoneMessageApi {
	
	private final static Logger LOG = LoggerFactory.getLogger(AkkaMessageApi.class);
	
	private ActorRef msgRouter;

	@Override
	public void sendToClient(JsonMessage message) {
		LOG.debug("sendToClient: {}", message);
		// TODO Auto-generated method stub

	}

	@Override
	public void sendToActiveClientsInRange(EntityJsonMessage message) {
		LOG.debug("sendToActiveClientsInRange: {}", message);
		// TODO Auto-generated method stub

	}

	@Override
	public void sendToEntity(long entityId, Object msg) {
		LOG.debug("sendToEntity: id: {}, msg: {}", entityId, msg);
		// TODO Auto-generated method stub

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
