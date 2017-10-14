package net.bestia.zoneserver.actor.zone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.JsonMessage;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.connection.ClientConnectionActor;
import net.bestia.zoneserver.actor.connection.ConnectionManagerActor;

/**
 * This actor will send the message towards a client. In order to do so it will
 * retrieve the connected webserver connection and send the message to this
 * server.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class SendClientActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "sendClient";


	@Autowired
	public SendClientActor() {


	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(JsonMessage.class, this::sendToClient)
				.build();
	}

	/**
	 * Sends to all active players in range. Maybe automatically detecting this
	 * method by message type does not work out. Then we might need to create a
	 * whole new actor only responsible for sending active range messages. Might
	 * be better idea anyways.
	 * 
	 * @param msg
	 */
	private void sendToClient(JsonMessage msg) {
		LOG.debug("Sending message to client: {}", msg);
		
		if (msg.getAccountId() == 0) {
			LOG.warning("AccID 0 is not valid. Message wont get delivered.");
			return;
		}

		final String actorName = AkkaCluster.getNodeName(
				IngestExActor.NAME,
				ConnectionManagerActor.NAME,
				ClientConnectionActor.getActorName(msg.getAccountId()));

		final ActorSelection actor = context.actorSelection(actorName);
		actor.tell(msg, ActorRef.noSender());
	}
}
