package bestia.zoneserver.actor.zone;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.sharding.ClusterSharding;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import bestia.messages.ClientFromMessageEnvelope;
import bestia.server.EntryActorNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Central influx point for web clients. The incoming messages are resend
 * towards the connection actors which manage the client connections.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class WebIngestActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public static final String NAME = "ingest";

	private final ActorRef clients;

	@Autowired
	public WebIngestActor() {

		final ClusterSharding sharding = ClusterSharding.get(context().system());
		this.clients = sharding.shardRegion(EntryActorNames.SHARD_CONNECTION);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(ClientFromMessageEnvelope.class, msg -> {
			LOG.debug("Received message from remote: {}", msg);
			clients.tell(msg, getSender());
		}).build();
	}
}
