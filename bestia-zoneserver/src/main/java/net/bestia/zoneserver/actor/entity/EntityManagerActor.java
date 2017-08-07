package net.bestia.zoneserver.actor.entity;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.EntityMessage;
import net.bestia.messages.internal.entity.ComponentPayloadWrapper;
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.IngestExActor;
import net.bestia.zoneserver.actor.zone.IngestExActor.RedirectMessage;

/**
 * It manages to start the entity actor as cluster sharded actors. This worker
 * has a fixed address and can thus be used from the other actors quite easily.
 * It is the central point for creating entity centric actors.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class EntityManagerActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

	public static final String NAME = "entity";

	// private ActorRef entityShardRegion;

	public EntityManagerActor() {
		// no op.
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Long.class, this::startEntityActor)
				.match(ComponentPayloadWrapper.class, this::onEntityComponentMessage)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		// entityShardRegion =
		// ClusterSharding.get(getContext().system()).shardRegion("entity");

		// After the start we must inform the ingest actor that we want to
		// receive messages.
		final RedirectMessage msg = RedirectMessage.get(EntityMessage.class, ComponentPayloadWrapper.class);
		AkkaSender.sendToActor(getContext(), IngestExActor.NAME, msg, getSelf());
	}

	/**
	 * Entity message should be forwarded towards the shard containing this id.
	 */
	public void onEntityComponentMessage(ComponentPayloadWrapper msg) {
		LOG.debug("Received: {}.", msg);
		// entityShardRegion.tell(msg, getSelf());

		// Currenty we dont use sharding only send to local system.
		AkkaSender.sendEntityActor(getContext(), msg.getEntityId(), msg);
	}

	/**
	 * Starts a new entity actor in the system.
	 * 
	 * @param entityId
	 */
	private void startEntityActor(Long entityId) {

		final String actorName = EntityActor.getActorName(entityId);

		LOG.debug("Received start request for entity: {}. Actor name: {}", entityId, actorName);

		final ActorRef entityActor = SpringExtension.actorOf(getContext(), EntityActor.class, actorName, entityId);
		
		LOG.debug("Started actor: {}", entityActor);
	}

}
