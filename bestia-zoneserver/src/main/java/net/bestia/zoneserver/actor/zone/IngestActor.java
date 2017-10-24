package net.bestia.zoneserver.actor.zone;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.sharding.ClusterSharding;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.server.EntryActorNames;

/**
 * Central ingestion point for web clients. The incoming messages are wrapped
 * and send to the parent actor.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class IngestActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public static final String NAME = "ingest";
	
	private final ActorRef clientIngestActor;
	private final ActorRef clients;

	@Autowired
	public IngestActor(ActorRef clientIngestActor) {

		this.clientIngestActor = Objects.requireNonNull(clientIngestActor);
		final ClusterSharding sharding = ClusterSharding.get(getContext().getSystem());
		this.clients = sharding.shardRegion(EntryActorNames.SHARD_CONNECTION);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().matchAny(s -> {
			
			LOG.debug("Received message from remote: {}", s);
			clients.tell(s, getSender());
			
		}).build();
	}

}
