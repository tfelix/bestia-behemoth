package net.bestia.zoneserver.actor.zone;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.sharding.ClusterSharding;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.JsonMessage;
import net.bestia.server.EntryActorNames;

/**
 * This actor sends the incoming message towards the registered cluster sharding
 * actor which holds the connection to a client.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class SendClientActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "sendToClient";

	private ActorRef clientConnection;

	public SendClientActor() {
		// no op.
	}

	@Override
	public void preStart() throws Exception {
		clientConnection = ClusterSharding.get(getContext().getSystem()).shardRegion(EntryActorNames.SHARD_CONNECTION);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(JsonMessage.class, msg -> {
					LOG.debug("Sending to client: {}", msg);
					clientConnection.tell(msg, getSender());
				})
				.build();
	}
}
