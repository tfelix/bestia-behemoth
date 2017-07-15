package net.bestia.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.entity.component.Component;
import net.bestia.entity.recycler.ComponentDeleter;
import net.bestia.entity.recycler.EntityCache;
import net.bestia.messages.internal.entity.EntityDeleteInternalMessage;
import net.bestia.zoneserver.actor.ZoneAkkaApi;

/**
 * The entity deleter is performing several cleanup acts for entities. It stops
 * the entity actor if components are removed and as well deletes the components
 * from the services if this is needed.
 * 
 * The components and entities are cached for later reuse by the
 * {@link EntityCache}.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class EntityDeleterService {
	
	private static final Logger LOG = LoggerFactory.getLogger(EntityDeleterService.class);

	private final EntityCache cache;
	private final EntityService entityService;
	private final ZoneAkkaApi akkaApi;
	private final Map<String, ComponentDeleter<? extends Component>> componentDeleter = new HashMap<>();

	@Autowired
	public EntityDeleterService(EntityCache cache,
			EntityService entityService,
			ZoneAkkaApi akkaApi,
			List<ComponentDeleter<? extends Component>> deleters) {

		this.cache = Objects.requireNonNull(cache);
		this.entityService = Objects.requireNonNull(entityService);
		this.akkaApi = Objects.requireNonNull(akkaApi);

		for (ComponentDeleter<? extends Component> deleter : deleters) {

			componentDeleter.put(deleter.supportedComponent().getName(), deleter);

		}
	}

	/**
	 * Deletes the entity and removes all components from the system.
	 * 
	 * @param entity
	 *            The entity to remove from the system.
	 */
	public void deleteEntity(Entity entity) {

		LOG.debug("Deleting entity: {}", entity);
		
		// Iterate over all components and remove them.
		for (Component component : entityService.getAllComponents(entity)) {

			final String clazzname = component.getClass().getName();
			if (componentDeleter.containsKey(clazzname)) {
				componentDeleter.get(clazzname).freeComponent(component);
				cache.stashComponente(component);
			}

		}

		// Send message to kill off entity actor.
		final EntityDeleteInternalMessage killMsg = new EntityDeleteInternalMessage(entity.getId());
		akkaApi.sendEntityActor(entity.getId(), killMsg);

		// Delete entity.
		entityService.delete(entity);

		cache.stashEntity(entity);
	}

	/**
	 * Deletes a single component from the entity.
	 * 
	 * @param entity
	 *            The entity to delete the component from.
	 * @param clazz
	 *            The clazz/component type to delete.
	 */
	public void deleteComponent(Entity entity, Class<? extends Component> clazz) {

		LOG.debug("Deleting component: {} from entity: {}.", clazz.getSimpleName(), entity);
		
		final Component comp = entityService.getComponent(entity, clazz)
				.orElseThrow(IllegalArgumentException::new);

		if(componentDeleter.containsKey(clazz.getName())) {
			componentDeleter.get(clazz.getName()).freeComponent(comp);
			cache.stashComponente(comp);
		}
		
		entityService.deleteComponent(comp);
	}
}
