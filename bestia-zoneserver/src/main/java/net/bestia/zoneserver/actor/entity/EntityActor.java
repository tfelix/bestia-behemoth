package net.bestia.zoneserver.actor.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.component.EntityComponentActorFactory;
import net.bestia.messages.internal.entity.ComponentPayloadWrapper;
import net.bestia.messages.internal.entity.EntityComponentMessage;
import net.bestia.messages.internal.entity.EntityComponentMessage.ComponentState;

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
public class EntityActor extends AbstractActor {
	private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

	private final EntityComponentActorFactory factory;

	/**
	 * Saves the references for the actors managing components.
	 */
	private Map<Long, ActorRef> actorsByComponentId = new HashMap<>();
	private Map<ActorRef, Long> componentIdsByActor = new HashMap<>();
	
	private long entityId;

	@Autowired
	public EntityActor(EntityComponentActorFactory factory) {

		this.factory = Objects.requireNonNull(factory);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Long.class, this::handleEntityIdStart)
				.match(EntityComponentMessage.class, this::handleComponentMessage)
				.match(ComponentPayloadWrapper.class, this::handleComponentPayload)
				.match(Terminated.class, this::handleTerminated)
				.build();
	}

	private void handleEntityIdStart(Long entityId) {
		LOG.debug("Started for entity: {}.", entityId);
		
		this.entityId = entityId;
	}

	/**
	 * Checks if we have such a associated component and if so delivers the
	 * message.
	 */
	private void handleComponentPayload(ComponentPayloadWrapper msg) {

		final long compId = msg.getComponentId();

		if (!actorsByComponentId.containsKey(compId)) {
			LOG.debug("Component message unhandled: {}.", msg);
			unhandled(msg);
			return;
		}

		final ActorRef compActor = actorsByComponentId.get(compId);
		LOG.debug("Forwarding comp message: {} to: {}.", msg, compActor);
		compActor.tell(msg.getPayload(), getSelf());
	}

	/**
	 * Adds or removes a active component actor from the entity.
	 * 
	 * @param msg
	 */
	private void handleComponentMessage(EntityComponentMessage msg) {

		if (msg.getState() == ComponentState.INSTALL) {
			LOG.debug("Installing component: {} on entity: {}.", msg.getComponentId(), entityId);

			// Install the component.
			final ActorRef compActor = factory.startActor(getContext(), msg.getComponentId());

			if (compActor == null) {
				LOG.warning("Component actor for comp id {} was not created.", msg.getComponentId());
				return;
			}

			context().watch(compActor);

			actorsByComponentId.put(msg.getComponentId(), compActor);
			componentIdsByActor.put(compActor, msg.getComponentId());

		} else {
			LOG.debug("Removing component: {} on entity: {}.", msg.getComponentId(), entityId);

			// Remove the component.
			final ActorRef compRef = actorsByComponentId.get(msg.getComponentId());

			if (compRef == null) {
				LOG.debug("Actor for component {} not found.", msg.getComponentId());
				return;
			}

			compRef.tell(PoisonPill.getInstance(), getSelf());
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
		final long compId = componentIdsByActor.get(termActor);

		componentIdsByActor.remove(termActor);
		actorsByComponentId.remove(compId);
	}
}
