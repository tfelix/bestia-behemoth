package net.bestia.zoneserver.actor.entity.component;

import akka.actor.AbstractActor;
import akka.actor.ActorContext;
import akka.actor.ActorRef;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.ActorSync;
import net.bestia.entity.component.Component;
import net.bestia.zoneserver.actor.SpringExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

/**
 * Depending on the given component ID this factory will create an actor
 * suitable for the component. This is done by checking the annotation
 * of the component.
 * 
 * @author Thomas Felix
 *
 */
@org.springframework.stereotype.Component
public class EntityComponentActorFactory {

	private static final Logger LOG = LoggerFactory.getLogger(EntityComponentActorFactory.class);

	private final EntityService entityService;

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

		return startActorByAnnotation(ctx, comp);
	}

	private ActorRef startActorByAnnotation(ActorContext ctx, Component comp) {
		if (!comp.getClass().isAnnotationPresent(ActorSync.class)) {
			LOG.warn("Component {} (id: {}) has no ComponentActor annotation. Can not create Actor.",
					comp.getClass().getName(), comp.getId());
			return null;
		}

		final ActorSync compActor = comp.getClass().getAnnotation(ActorSync.class);
		try {
			@SuppressWarnings("unchecked")
			final Class<? extends AbstractActor> actorClass = (Class<? extends AbstractActor>) Class.forName(compActor.value());
			
			final ActorRef actorRef = SpringExtension.actorOf(ctx,
					actorClass,
					null,
					comp.getEntityId(),
					comp.getId());

			LOG.debug("Starting ComponentActor: {} ({}) for entity: {}.",
					actorClass.getSimpleName(),
					comp.getId(),
					comp.getEntityId());

			return actorRef;
		} catch (ClassNotFoundException e) {
			LOG.warn("Could not start ComponentActor. Class not found: {}.", compActor.value());
			return null;
		}
	}
}
