package net.bestia.zoneserver.actor.zone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.sharding.ClusterSharding;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.AccountMessage;
import net.bestia.messages.internal.FromClient;
import net.bestia.server.EntryActorNames;

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

		final ClusterSharding sharding = ClusterSharding.get(getContext().getSystem());
		this.clients = sharding.shardRegion(EntryActorNames.SHARD_CONNECTION);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(AccountMessage.class, msg -> {
			LOG.debug("Received message from remote: {}", msg);
			clients.tell(new FromClient(msg), getSender());
		}).build();
	}
}
