package net.bestia.zoneserver.actor;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.actor.zone.ActiveClientUpdateActor;
import net.bestia.zoneserver.actor.zone.SendClientActor;

/**
 * Should be the base class for the whole akka system. This class provides some
 * helper methods to simply create dependency injected actors via spring.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public abstract class BestiaActor extends AbstractActor {

	public BestiaActor() {
		super();
	}

	/**
	 * This will deliver the given message back to the account. In order to do
	 * this a {@link SendClientActor} responder is used. The actor will be
	 * created when necessary (this means the method is first invoked).
	 * 
	 * @param msg
	 * @deprecated Use AkkaSender.sendClient
	 */
	protected void sendClient(JsonMessage msg) {
		
		AkkaSender.sendClient(getContext(), msg);
	}

	/**
	 * Sends the given message back to all active player clients in sight. To to
	 * this an on demand {@link ActiveClientUpdateActor} is created.
	 * 
	 * @deprecated Use AkkaSender.sendActiveInRange
	 * @param msg
	 *            The update message to be send to all active clients in sight
	 *            of the referenced entity.
	 */
	protected void sendActiveInRangeClients(EntityJsonMessage msg) {
		
		AkkaSender.sendActiveInRangeClients(getContext(), msg);
	}
}