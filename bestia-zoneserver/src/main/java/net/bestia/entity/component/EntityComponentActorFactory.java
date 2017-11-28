package net.bestia.entity.component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import akka.actor.AbstractActor;
import akka.actor.ActorContext;
import akka.actor.ActorRef;
import net.bestia.entity.EntityService;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.entity.component.MovementComponentActor;
import net.bestia.zoneserver.actor.entity.component.ScriptComponentActor;
import net.bestia.zoneserver.actor.entity.component.StatusComponentActor;

/**
 * Depending on the given component ID this factory will create an actor
 * suitable for the component. This is currently done by checking the annotation
 * of the component.
 * 
 * @author Thomas Felix
 *
 */
@org.springframework.stereotype.Component
public class EntityComponentActorFactory {

	private static final Logger LOG = LoggerFactory.getLogger(EntityComponentActorFactory.class);

	private final EntityService entityService;

	private static final Map<Class<? extends Component>, Class<? extends AbstractActor>> ACTOR_FOR_COMPONENTS;

	static {
		Map<Class<? extends Component>, Class<? extends AbstractActor>> actorForComp = new HashMap<>();

		actorForComp.put(StatusComponent.class, StatusComponentActor.class);
		actorForComp.put(ScriptComponent.class, ScriptComponentActor.class);
		actorForComp.put(PositionComponent.class, MovementComponentActor.class);

		ACTOR_FOR_COMPONENTS = Collections.unmodifiableMap(actorForComp);
	}

	@Autowired
	public EntityComponentActorFactory(EntityService entityService) {

		this.entityService = Objects.requireNonNull(entityService);
	}

	/**
	 * Starts a component actor which is responsible for managing continues
	 * callback to some component code.
	 * 
	 * @param componentId
	 *            The ID of the already saved and existing component to create
	 *            an actor for.
	 * @return The created actor or null if something went wrong.
	 */
	public ActorRef startActor(ActorContext ctx, long componentId) {

		final Component comp = entityService.getComponent(componentId);

		if (comp == null) {
			LOG.warn("Component {} does not exist. Can not build component actor for it.", componentId);
			return null;
		}

		ActorRef actor = startActorByAnnotation(ctx, comp);

		// Das hier bald weglassen.
		if (actor == null) {
			actor = startActorByLegacyLookup(ctx, comp);
		}

		return actor;
	}

	private ActorRef startActorByAnnotation(ActorContext ctx, Component comp) {
		if (!comp.getClass().isAnnotationPresent(ComponentActor.class)) {
			LOG.warn("Component {} (id: {}) has now ComponentActor annotation. Can not create Actor.",
					comp.getClass().getName(), comp.getId());
			return null;
		}

		final ComponentActor compActor = comp.getClass().getAnnotation(ComponentActor.class);
		try {
			final Class<?> actorClass = Class.forName(compActor.value());
			final ActorRef actorRef = buildActor(ctx, comp);

			LOG.debug("Starting componenent actor: {} ({}) for entity: {}.",
					actorClass.getSimpleName(),
					comp.getId(),
					comp.getEntityId());

			return actorRef;
		} catch (ClassNotFoundException e) {
			LOG.warn("Could not start ComponentActor. Class not found: {}.", compActor.value());
			return null;
		}
	}

	/**
	 * Old version to start actors. Will be replaced by annotation.
	 * 
	 * @deprecated
	 */
	private ActorRef startActorByLegacyLookup(ActorContext ctx, Component comp) {

		final Class<? extends Component> compClazz = comp.getClass();

		if (!ACTOR_FOR_COMPONENTS.containsKey(compClazz)) {
			LOG.warn("Component {} (id: {}) has now factory module attached.", compClazz.getName(), comp.getId());
			return null;
		}

		final ActorRef compActor = buildActor(ctx, comp);

		LOG.debug("Starting componenent actor: {} ({}) for entity: {}.", compClazz.getSimpleName(), comp.getId(),
				comp.getEntityId());

		return compActor;
	}

	/**
	 * Creates the actor.
	 */
	private ActorRef buildActor(ActorContext ctx, Component comp) {

		final Class<? extends AbstractActor> actorClass = ACTOR_FOR_COMPONENTS.get(comp.getClass());

		return SpringExtension.actorOf(ctx,
				actorClass,
				null,
				comp.getEntityId());
	}
}
