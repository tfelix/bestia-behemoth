package net.bestia.entity.component.interceptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.Component;

/**
 * Main class for collecting and triggering component interaction interceptions.
 * 
 * @author Thomas Felix
 *
 */
@org.springframework.stereotype.Component
public class Interceptor {

	private static final Logger LOG = LoggerFactory.getLogger(Interceptor.class);
	private final Map<Class<? extends Component>, List<BaseComponentInterceptor<? extends Component>>> interceptors = new HashMap<>();

	@Autowired
	public Interceptor(List<BaseComponentInterceptor<? extends Component>> interceptors) {
		
		interceptors.forEach(this::addInterceptor);
	}
	
	/**
	 * Adds an intercepter which gets notified if certain components will
	 * change. He then can perform actions like update the clients in range
	 * about the occurring component change.
	 * 
	 * @param interceptor
	 *            The intercepter to listen to certain triggering events.
	 */
	public void addInterceptor(BaseComponentInterceptor<? extends Component> interceptor) {
		Objects.requireNonNull(interceptor);
		LOG.debug("Adding intercetor: {}.", interceptor);

		final Class<? extends Component> triggerType = interceptor.getTriggerType();

		if (!interceptors.containsKey(triggerType)) {
			interceptors.put(triggerType, new ArrayList<>());
		}

		interceptors.get(triggerType).add(interceptor);
	}

	/**
	 * Checks if the entity owns the component. If not an
	 * {@link IllegalArgumentException} is thrown.
	 * 
	 * @param e
	 * @param c
	 */
	private boolean ownsComponent(Entity e, Component c) {
		if (e.getId() != c.getEntityId()) {
			LOG.warn("Component {} is not owned by entity: {}.", c, e);
			return false;
		}

		return true;
	}

	public void interceptUpdate(EntityService entityService, Entity entity, Component component) {
		if (!ownsComponent(entity, component)) {
			return;
		}

		// Check possible interceptors.
		if (interceptors.containsKey(component.getClass())) {
			LOG.debug("Intercepting update component {} for: {}.", component, entity);

			interceptors.get(component.getClass()).forEach(intercep -> {
				// Need to cast so we dont get problems with typings.
				intercep.triggerUpdateAction(entityService, entity, component);
			});
		}
	}

	public void interceptCreated(EntityService entityService, Entity entity, Component component) {
		if (!ownsComponent(entity, component)) {
			return;
		}

		if (interceptors.containsKey(component.getClass())) {
			LOG.debug("Intercepting created component {} for: {}.", component, entity);

			interceptors.get(component.getClass()).forEach(intercep -> {
				// Need to cast so we dont get problems with typings.
				intercep.triggerCreateAction(entityService, entity, component);
			});
		}
	}

	public void interceptDeleted(EntityService entityService, Entity entity, Component component) {
		if (!ownsComponent(entity, component)) {
			return;
		}

		// Check possible interceptors.
		if (interceptors.containsKey(component.getClass())) {
			LOG.debug("Intercepting update component {} for: {}.", component, entity);

			interceptors.get(component.getClass()).forEach(intercep -> {
				intercep.triggerDeleteAction(entityService, entity, component);
			});
		}
	}
}
