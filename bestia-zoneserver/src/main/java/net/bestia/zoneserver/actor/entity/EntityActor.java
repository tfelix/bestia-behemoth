package net.bestia.zoneserver.actor.entity;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.component.EntityComponentActorFactory;
import net.bestia.messages.EntityMessage;
import net.bestia.messages.internal.entity.ComponentEnvelope;
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
	private BiMap<Long, ActorRef> componentActors = HashBiMap.create();

	private long entityId;

	@Autowired
	public EntityActor(EntityComponentActorFactory factory) {

		this.factory = Objects.requireNonNull(factory);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(EntityComponentMessage.class, this::handleComponentMessage)
				.match(ComponentEnvelope.class, this::handleComponentPayload)
				.match(Terminated.class, this::handleTerminated)
				.build();
	}

	/**
	 * Checks if we have such a associated component and if so delivers the
	 * message.
	 */
	private void handleComponentPayload(ComponentEnvelope msg) {
		checkStartup(msg);

		final long compId = msg.getComponentId();

		if (!componentActors.containsKey(compId)) {
			LOG.debug("Component message unhandled: {}.", msg);
			unhandled(msg);
			return;
		}

		final ActorRef compActor = componentActors.get(compId);
		LOG.debug("Forwarding comp message: {} to: {}.", msg, compActor);
		compActor.tell(msg.getPayload(), getSelf());
	}

	/**
	 * Adds or removes a active component actor from the entity.
	 * 
	 * @param msg
	 */
	private void handleComponentMessage(EntityComponentMessage msg) {
		checkStartup(msg);

		if (msg.getState() == ComponentState.INSTALL) {
			installComponent(msg.getComponentId());
		} else {
			removeComponent(msg.getComponentId());
		}
	}

	private void installComponent(long componentId) {
		LOG.debug("Installing component: {} on entity: {}.", componentId, entityId);

		// Install the component.
		final ActorRef compActor = factory.startActor(getContext(), componentId);

		if (compActor == null) {
			LOG.warning("Component actor for comp id {} was not created.", componentId);
			return;
		}

		context().watch(compActor);
		componentActors.put(componentId, compActor);
	}

	private void removeComponent(long componentId) {
		LOG.debug("Removing component: {} on entity: {}.", componentId, entityId);

		// Remove the component.
		final ActorRef compRef = componentActors.getOrDefault(componentId, ActorRef.noSender());
		compRef.tell(PoisonPill.getInstance(), getSelf());
	}

	/**
	 * Check which actor was terminated and remove it.
	 * 
	 * @param term
	 *            The termination message from akka.
	 */
	private void handleTerminated(Terminated term) {

		componentActors.inverse().remove(term.actor());
	}

	/**
	 * Checks if we have already a defined entity id and if not we use it.
	 */
	private void checkStartup(EntityMessage msg) {
		if (entityId == 0) {
			entityId = msg.getEntityId();
		}
	}
}
