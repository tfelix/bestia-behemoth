package net.bestia.zoneserver;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.MessageApi;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.entity.EntityActor;
import net.bestia.zoneserver.actor.entity.EntityShardManagerActor;
import net.bestia.zoneserver.actor.zone.SendActiveClientsActor;

/**
 * Contains static helper methods to send special messages to certain actors
 * inside the bestia system. This is only a helper shortcut to allow easier and
 * faster access to send messages to a bunch of actors which are accessed by
 * special names.
 * 
 * Usually this class is used from actors itself. From the outside the
 * {@link MessageApi} is used.
 * 
 * @author Thomas Felix
 *
 */
public final class AkkaSender {

	private AkkaSender() {
		// no op.
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

		sendToActor(context, SendActiveClientsActor.NAME, msg);
	}

	/**
	 * Sends a message directly to a entity shard manager actor. He will
	 * re-deliver the message to the correct shard.
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
		final String actorName = AkkaCluster.getNodeName(EntityShardManagerActor.NAME, entityActorName);
		final ActorSelection selection = context.system().actorSelection(actorName);
		selection.tell(msg, ActorRef.noSender());
	}
}
