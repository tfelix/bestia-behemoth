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
import net.bestia.messages.cluster.entity.ComponentPayloadWrapper;
import net.bestia.messages.cluster.entity.EntityComponentMessage;
import net.bestia.messages.cluster.entity.EntityComponentMessage.ComponentState;

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

	private final static String ACTOR_NAME_TEMPLATE = "entity-%d";

	private final EntityComponentActorFactory factory;

	/**
	 * Saves the references for the actors managing components.
	 */
	private Map<Long, ActorRef> actorsByComponentId = new HashMap<>();
	private Map<ActorRef, Long> componentIdsByActor = new HashMap<>();

	@Autowired
	public EntityActor(long entityId, EntityComponentActorFactory factory) {

		this.factory = Objects.requireNonNull(factory);
		// this.entityId = entityId;
	}

	/**
	 * Returns the unique actor name based on the entity id.
	 * 
	 * @param entityId
	 * @return
	 */
	public static String getActorName(long entityId) {
		if (entityId < 0) {
			throw new IllegalArgumentException("Entity id must be positive.");
		}
		return String.format(ACTOR_NAME_TEMPLATE, entityId);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(EntityComponentMessage.class, this::handleComponentMessage)
				.match(ComponentPayloadWrapper.class, this::handleComponentPayload)
				.match(Terminated.class, this::handleTerminated)
				.matchAny(this::handleAll)
				.build();
	}

	private void handleAll(Object msg) {
		LOG.debug("MSG: {}.", msg);
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
