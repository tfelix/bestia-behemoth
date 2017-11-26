package net.bestia.zoneserver.actor.zone;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.sharding.ClusterSharding;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.EntityMessage;
import net.bestia.server.EntryActorNames;

/**
 * This actor sends the incoming message towards the registered cluster sharding
 * actor which manages an entity.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class SendEntityActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "sendToEntity";

	private ActorRef entityActorShard;

	public SendEntityActor() {
		// no op.
	}

	@Override
	public void preStart() throws Exception {
		entityActorShard = ClusterSharding.get(getContext().getSystem()).shardRegion(EntryActorNames.SHARD_ENTITY);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(EntityMessage.class, msg -> {
					LOG.debug("Sending to entty: {}", msg);
					entityActorShard.tell(msg, getSender());
				})
				.build();
	}
}
