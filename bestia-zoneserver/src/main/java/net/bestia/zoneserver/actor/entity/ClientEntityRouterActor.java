package net.bestia.zoneserver.actor.entity;

import akka.actor.AbstractActor;
import net.bestia.messages.EntityJsonMessage;

/**
 * This actor will redirect all incoming client messages to the designated
 * entity.
 * 
 * @author Thomas Felix
 *
 */
public class ClientEntityRouterActor extends AbstractActor {

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(EntityJsonMessage.class, this::onEntityMessage)
				.build();
	}

	private void onEntityMessage(EntityJsonMessage msg) {
		
		
		
	}
}
