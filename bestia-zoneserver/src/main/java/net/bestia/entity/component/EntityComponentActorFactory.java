package net.bestia.entity.component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import net.bestia.entity.EntityService;

/**
 * Depending on the given component ID this factory will create an actor
 * suitable for the component.
 * 
 * @author Thomas Felix
 *
 */
@org.springframework.stereotype.Component
public class EntityComponentActorFactory {

	private static final Logger LOG = LoggerFactory.getLogger(EntityComponentActorFactory.class);

	private final EntityService entityService;

	private final Map<String, ActorComponentFactoryModule<? extends Component>> componentModules = new HashMap<>();

	@Autowired
	public EntityComponentActorFactory(EntityService entityService,
			List<ActorComponentFactoryModule<? extends Component>> modules) {
		Objects.requireNonNull(modules);

		this.entityService = Objects.requireNonNull(entityService);

		for (ActorComponentFactoryModule<? extends Component> module : modules) {
			componentModules.put(module.buildActorFor().getName(), module);
		}
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

		final String compClazzName = comp.getClass().getName();

		if (!componentModules.containsKey(compClazzName)) {
			LOG.warn("Component {} (id: {}) has now factory module attached.", compClazzName, componentId);
			return null;
		}

		return componentModules.get(compClazzName).buildActor(ctx, comp);
	}

}
