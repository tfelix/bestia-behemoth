package net.bestia.zoneserver;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.entity.EntityActor;
import net.bestia.zoneserver.actor.entity.EntityManagerActor;
import net.bestia.zoneserver.actor.zone.ActiveClientUpdateActor;
import net.bestia.zoneserver.actor.zone.SendActiveRangeActor;
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;

/**
 * Contains static helper methods to send special messages to certain actors
 * inside the bestia system. This is only a helper shortcut to allow easier and
 * faster access to send messages to a bunch of actors which are accessed by
 * special names.
 * 
 * Usually this class is used from actors itself. From the outside the
 * {@link ZoneAkkaApi} is used.
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

		sendToActor(context, SendClientActor.NAME, msg);
	}

	public static void sendToActor(ActorContext context, String actorName, Object message) {
		sendToActor(context, actorName, message, ActorRef.noSender());
	}

	public static void sendToActor(ActorContext context, String actorName, Object message, ActorRef sender) {

		final String nodeName = AkkaCluster.getNodeName(actorName);
		final ActorSelection actor = context.actorSelection(nodeName);
		actor.tell(message, sender);
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

		sendToActor(context, SendActiveRangeActor.NAME, msg);
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
		final String entityActorName = EntityActor.getActorName(entityId);
		final String actorName = AkkaCluster.getNodeName(EntityManagerActor.NAME, entityActorName);
		final ActorSelection selection = context.system().actorSelection(actorName);
		selection.tell(msg, ActorRef.noSender());
	}
}
