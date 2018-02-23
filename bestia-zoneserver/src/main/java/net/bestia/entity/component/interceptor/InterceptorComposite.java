package net.entity.component.interceptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.bestia.entity.component.interceptor.BaseComponentInterceptor;
import net.bestia.entity.component.interceptor.Interceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.Component;

/**
 * Main class for collecting and triggering component interaction interceptions.
 *
 * @author Thomas Felix
 */
public class InterceptorComposite implements Interceptor {

	private static final Logger LOG = LoggerFactory.getLogger(InterceptorComposite.class);
	
	private final Map<Class<? extends Component>, List<BaseComponentInterceptor>> interceptors = new HashMap<>();
	private final List<BaseComponentInterceptor> defaultInterceptors = new ArrayList<>();

	public InterceptorComposite(List<BaseComponentInterceptor<? extends Component>> interceptors) {
		Objects.requireNonNull(interceptors);
		interceptors.forEach(this::addInterceptor);
	}

	/**
	 * Adds an interceptor which gets notified if certain components will
	 * change. He then can perform actions like update the clients in range
	 * about the occurring component change.
	 *
	 * @param interceptor The intercepter to listen to certain triggering events.
	 */
	public void addInterceptor(BaseComponentInterceptor<? extends Component> interceptor) {
		Objects.requireNonNull(interceptor);
		LOG.debug("Adding intercetor: {}.", interceptor.getClass().getName());

		final Class<? extends Component> triggerType = interceptor.getTriggerType();

		if (!interceptors.containsKey(triggerType)) {
			interceptors.put(triggerType, new ArrayList<>());
		}

		interceptors.get(triggerType).add(interceptor);
	}

	/**
	 * The default interceptor are called upon each component update. Regardless of their type.
	 * Thus they are handy is no type specific components need to be scanned.
	 */
	public void addDefaultInterceptor(BaseComponentInterceptor interceptor) {
		defaultInterceptors.add(interceptor);
	}

	/**
	 * Checks if the entity owns the component. If not an
	 * {@link IllegalArgumentException} is thrown.
	 */
	private boolean ownsComponent(Entity e, Component c) {
		if (e.getId() != c.getEntityId()) {
			LOG.warn("Component {} is not owned by entity: {}.", c, e);
			return false;
		}

		return true;
	}

	@Override
	public void interceptUpdate(EntityService entityService, Entity entity, Component component) {
		if (!ownsComponent(entity, component)) {
			return;
		}

		defaultInterceptors.forEach(i -> {
			i.triggerUpdateAction(entityService, entity, component);
		});

		// Check possible interceptors.
		if (interceptors.containsKey(component.getClass())) {
			LOG.debug("Intercepting update component {} for: {}.", component, entity);

			interceptors.get(component.getClass()).forEach(intercep -> {
				// Need to cast so we dont get problems with typings.
				intercep.triggerUpdateAction(entityService, entity, component);
			});
		}
	}

	@Override
	public void interceptCreated(EntityService entityService, Entity entity, Component component) {
		if (!ownsComponent(entity, component)) {
			return;
		}

		defaultInterceptors.forEach(i -> {
			i.triggerCreateAction(entityService, entity, component);
		});

		if (interceptors.containsKey(component.getClass())) {
			LOG.debug("Intercepting created component {} for: {}.", component, entity);

			interceptors.get(component.getClass()).forEach(intercep -> {
				// Need to cast so we dont get problems with typings.
				intercep.triggerCreateAction(entityService, entity, component);
			});
		}
	}

	@Override
	public void interceptDeleted(EntityService entityService, Entity entity, Component component) {
		if (!ownsComponent(entity, component)) {
			return;
		}

		defaultInterceptors.forEach(i -> {
			i.triggerDeleteAction(entityService, entity, component);
		});

		// Check possible interceptors.
		if (interceptors.containsKey(component.getClass())) {
			LOG.debug("Intercepting update component {} for: {}.", component, entity);

			interceptors.get(component.getClass()).forEach(intercep -> intercep.triggerDeleteAction(entityService, entity, component));
		}
	}
}
