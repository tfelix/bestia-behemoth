package net.bestia.zoneserver;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.entity.EntityActor;
import net.bestia.zoneserver.actor.entity.EntityWorker;
import net.bestia.zoneserver.actor.zone.ActiveClientUpdateActor;
import net.bestia.zoneserver.actor.zone.SendClientActor;

/**
 * Contains static helper methods to send special messages to certain actors
 * inside the bestia system. This is only a helper shortcut to allow easier and
 * faster access to send messages.
 * 
 * @author Thomas Felix
 *
 */
public final class AkkaSender {

	private AkkaSender() {
		// no op.
	}

	/**
	 * This will deliver the given message back to the account. In order to do
	 * this a {@link SendClientActor} responder is used. The actor will be
	 * created when necessary (this means the method is first invoked).
	 * 
	 * @param msg
	 */
	public static void sendClient(ActorContext context, JsonMessage msg) {

		final String nodeName = AkkaCluster.getNodeName(SendClientActor.NAME);
		final ActorSelection responder = context.actorSelection(nodeName);
		responder.tell(msg, ActorRef.noSender());
	}

	/**
	 * Sends the given message back to all active player clients in sight. To to
	 * this an on demand {@link ActiveClientUpdateActor} is created.
	 * 
	 * @param msg
	 *            The update message to be send to all active clients in sight
	 *            of the referenced entity.
	 */
	public static void sendActiveInRangeClients(ActorContext context, EntityJsonMessage msg) {

		final String nodeName = AkkaCluster.getNodeName(SendClientActor.NAME);
		final ActorSelection activeClientBroadcaster = context.actorSelection(nodeName);
		activeClientBroadcaster.tell(msg, ActorRef.noSender());
	}

	/**
	 * Sends a message directly to a entity actor.
	 * 
	 * @param context
	 *            An akka context.
	 * @param entityId
	 *            The ID to the entity actor.
	 * @param msg
	 *            The message.
	 */
	public static void sendEntityActor(ActorContext context, long entityId, Object msg) {
		// Find the name.
		final String rawActorName = EntityActor.getActorName(entityId);
		final String actorName = AkkaCluster.getNodeName(EntityWorker.NAME, rawActorName);
		final ActorSelection selection = context.system().actorSelection(actorName);
		selection.tell(msg, ActorRef.noSender());
	}
}
