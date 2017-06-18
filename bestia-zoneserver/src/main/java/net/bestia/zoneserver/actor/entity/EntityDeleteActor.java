package net.bestia.zoneserver.actor.entity;

import java.util.Objects;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorSelection;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.entity.EntityDeleteInternalMessage;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityRecycler;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.component.PositionComponent;

/**
 * The {@link EntityDeleteActor} actor are here as high level instance to
 * perform all the steps needed to remove an entity from the world. Like
 * terminating all running actors, deleting all components from the services and
 * possibly recycling them.
 * 
 * After all jobs are performed the entity delete actors terminates itself.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class EntityDeleteActor extends AbstractActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);
	
	private final long entityId;
	
	private final EntityRecycler recycler;
	private final EntityService entityService;

	public EntityDeleteActor(long entityId, EntityService entityService, EntityRecycler recycler) {

		this.entityId = entityId;
		this.recycler = Objects.requireNonNull(recycler);
		this.entityService = Objects.requireNonNull(entityService);
		
		startDeletingEntity();
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Terminated.class, this::handleActorTermination)
				.build();
	}

	private void handleActorTermination(Terminated msg) {
		
	}
	
	private void startDeletingEntity() {
		LOG.debug("Deleting/Recycling entity {}.", entityId);
		
		final Entity e = entityService.getEntity(entityId);
		
		final EntityDeleteInternalMessage deleteMsg = new EntityDeleteInternalMessage(entityId);
		
		// Send stop signal the the entity watch actors.
		final ActorSelection entityActor = context().actorSelection(String.format("user/entity-%d", entityId));
		entityActor.tell(deleteMsg, getSelf());

		// Find the position of the entity to be send inside the message.
		// The position is important because the entity is removed right here
		// but we need to send the update messages to the clients, which in turn
		// is dependent on the position.
		// We need therefore cache this data.
		final Point position = entityService.getComponent(e, PositionComponent.class)
				.map(PositionComponent::getPosition)
				.orElse(new Point(0, 0));

		recycler.free(e);

		// Send status update to all clients in range.
		//sendActiveInRangeClients(EntityUpdateMessage.getDespawnUpdate(entityId, position));
		
		// Actually we should wait for termination by the EntityActor.
		context().stop(getSelf());
	}
}
