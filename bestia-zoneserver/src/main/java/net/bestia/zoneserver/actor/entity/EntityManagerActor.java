package net.bestia.zoneserver.actor.entity;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.zoneserver.actor.SpringExtension;

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

	public EntityManagerActor() {
		// no op.
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Long.class, this::startEntityActor)
				.build();
	}

	/**
	 * Starts a new entity actor in the system.
	 * 
	 * @param entityId
	 */
	private void startEntityActor(Long entityId) {

		final String actorName = EntityActor.getActorName(entityId);

		LOG.debug("Received start request for entity: {}. Actor name: {}", entityId, actorName);

		SpringExtension.actorOf(getContext(), EntityActor.class, actorName, entityId);
	}

}
