package net.bestia.zoneserver.actor.entity;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.EntityMessage;
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.actor.SpringExtension;

/**
 * The job of this actor is to receive the incoming messages for this shard. The
 * incoming messages will get delivered to the entities.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class EntityShardManagerActor extends AbstractActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

	public static final String NAME = "entity";

	public EntityShardManagerActor() {
		// no op.
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Long.class, this::onEntityStartMessage)
				.match(EntityMessage.class, this::onEntityMessage)
				.build();
	}

	/**
	 * Entity component message should be forwarded towards the shard containing
	 * the entity actor.
	 */
	public void onEntityMessage(EntityMessage msg) {
		LOG.debug("Received: {}.", msg);

		// Currenty we dont use sharding only send to local system.
		AkkaSender.sendEntityActor(getContext(), msg.getEntityId(), msg);
	}

	/**
	 * Starts a new entity actor in the system.
	 * 
	 * @param entityId
	 */
	private void onEntityStartMessage(Long entityId) {

		final String actorName = EntityActor.getActorName(entityId);
		LOG.debug("Received start request for entity: {}. Actor name: {}", entityId, actorName);

		final ActorRef entityActor = SpringExtension.actorOf(getContext(), EntityActor.class, actorName, entityId);

		LOG.debug("Started actor: {}", entityActor);
	}
	
	/*
	 	private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

	public static final String NAME = EntryActorNames.ENTITY_MANAGER;

	private ActorRef entityShardRegion;

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
		entityShardRegion = ClusterSharding.get(getContext().system()).shardRegion("entity");

		// After the start we must inform the ingest actor that we want to
		// receive messages.
		final RedirectMessage msg = RedirectMessage.get(EntityMessage.class, ComponentPayloadWrapper.class);
		AkkaSender.sendToActor(getContext(), IngestExActor.NAME, msg, getSelf());
	}


	public void onEntityComponentMessage(ComponentPayloadWrapper msg) {
		LOG.debug("Received: {}.", msg);
		entityShardRegion.tell(msg, getSelf());

		// Currenty we dont use sharding only send to local system.
		AkkaSender.sendEntityActor(getContext(), msg.getEntityId(), msg);
	}

	private void startEntityActor(Long entityId) {

		final String actorName = EntityActor.getActorName(entityId);

		LOG.debug("Received start request for entity: {}. Actor name: {}", entityId, actorName);

		final ActorRef entityActor = SpringExtension.actorOf(getContext(), EntityActor.class, actorName, entityId);

		LOG.debug("Started actor: {}", entityActor);
	}
	*/
	 
}
