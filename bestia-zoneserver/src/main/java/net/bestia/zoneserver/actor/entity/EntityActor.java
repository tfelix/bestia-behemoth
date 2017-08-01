package net.bestia.zoneserver.actor.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.messages.internal.entity.EntityComponentMessage;
import net.bestia.messages.internal.entity.EntityComponentMessage.ComponentState;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.actor.entity.component.EntityComponentActorFactory;

/**
 * The {@link EntityActor} is a persistent actor managing all aspects of a
 * spawned entity. This means it will keep references to AI actors or attached
 * script actors. This actor has to be used as an persisted shared actor.
 * 
 * This actor has to react on certain incoming request messages like for example
 * attaching a ticking script to the entity.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class EntityActor extends BestiaActor {
	private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

	private final static String ACTOR_NAME = "entity-%d";
	private final long entityId;
	
	private final EntityComponentActorFactory factory;

	/**
	 * Saves the references for the actors managing components.
	 */
	private Map<Long, ActorRef> componentActors = new HashMap<>();

	@Autowired
	public EntityActor(long entityId, EntityComponentActorFactory factory) {

		this.factory = Objects.requireNonNull(factory);
		this.entityId = entityId;
	}

	public static String getActorName(long entityId) {
		if (entityId < 0) {
			throw new IllegalArgumentException("Entity id must be positive.");
		}
		return String.format(ACTOR_NAME, entityId);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(EntityComponentMessage.class, this::handleComponentMessage)
				//.match(EntityMoveMessage.class, this::handleClientMoveMessage)
				//.match(EntityMoveInternalMessage.class, this::handleInternalMoveMessage)
				.match(Terminated.class, this::handleTerminated)
				.build();
	}
	
	private void handleComponentMessage(EntityComponentMessage msg) {
		
		if(msg.getState() == ComponentState.INSTALL) {
			
			// Install the component.
			ActorRef compActor = factory.startActor(msg.getComponentId());
			context().watch(compActor);
			componentActors.put(msg.getComponentId(), compActor);
			
		} else {
			
			// Remove the component.
			final ActorRef compRef = componentActors.get(msg.getComponentId());
			
			if(compRef == null) {
				LOG.debug("Actor for component {} not found.", msg.getComponentId());
				return;
			}
			
			stopActor(compRef);
			componentActors.remove(msg.getComponentId());
		}
		
	}

	/**
	 * Check which actor was terminated and remove it.
	 * 
	 * @param term
	 *            The termination message from akka.
	 */
	private void handleTerminated(Terminated term) {

		final ActorRef termActor = term.actor();
		long foundKey = -1;

		for(Entry<Long, ActorRef> entries : componentActors.entrySet()) {
			
			if(entries.getValue().equals(termActor)) {
				foundKey = entries.getKey();
				break;
			}
		}
		
		if(foundKey == -1) {
			LOG.warning("Unknown actor {} has terminated. Cant handle event.",
					term.getActor().path().toStringWithoutAddress());
		} else {
			componentActors.remove(foundKey);
		}

	}


	/**
	 * Transforms msg to internal movement message.
	 */
	private void handleClientMoveMessage(EntityMoveMessage msg) {
		//handleInternalMoveMessage(new EntityMoveInternalMessage(entityId, msg.getPath()));
	}

	private void stopActor(ActorRef ref) {
		context().unwatch(ref);
		context().stop(ref);
	}
}
