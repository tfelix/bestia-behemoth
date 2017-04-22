package net.bestia.zoneserver.actor;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.server.AkkaCluster;
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
public abstract class BestiaActor extends UntypedActor {

	private ActorSelection responder;
	private ActorRef activeClientBroadcaster;

	public BestiaActor() {
		super();
	}

	/**
	 * This will deliver the given message back to the account. In order to do
	 * this a {@link SendClientActor} responder is used. The actor will be
	 * created when necessary (this means the method is first invoked).
	 * 
	 * @param msg
	 */
	protected void sendClient(JsonMessage msg) {
		
		if (responder == null) {
			responder = context().actorSelection(AkkaCluster.getNodeName(SendClientActor.NAME));
		}

		responder.tell(msg, getSelf());
	}

	/**
	 * Sends the given message back to all active player clients in sight. To to
	 * this an on demand {@link ActiveClientUpdateActor} is created.
	 * 
	 * @param msg
	 *            The update message to be send to all active clients in sight
	 *            of the referenced entity.
	 */
	protected void sendActiveInRangeClients(EntityJsonMessage msg) {
		if (activeClientBroadcaster == null) {
			activeClientBroadcaster = SpringExtension.actorOf(getContext(), ActiveClientUpdateActor.class);
		}

		activeClientBroadcaster.tell(msg, getSelf());
	}
}