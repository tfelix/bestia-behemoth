package net.bestia.entity;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import akka.actor.PoisonPill;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.deleter.ComponentDeleter;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;

/**
 * The entity deleter is performing several cleanup acts for entities. It stops
 * the entity actor if components are removed and as well deletes the components
 * from the services if this is needed.
 * 
 * The components and entities are cached for later reuse by the
 * {@link EntityCache}.
 * 
 * FIXME Mit dem EntityService kombinieren.
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
	private final Map<String, ComponentDeleter<? extends Component>> componentCleaner = new HashMap<>();

	@Autowired
	public EntityDeleterService(EntityCache cache,
			EntityService entityService,
			ZoneAkkaApi akkaApi,
			List<ComponentDeleter<? extends Component>> deleters) {

		this.cache = Objects.requireNonNull(cache);
		this.entityService = Objects.requireNonNull(entityService);
		this.akkaApi = Objects.requireNonNull(akkaApi);

		for (ComponentDeleter<? extends Component> deleter : deleters) {

			componentCleaner.put(deleter.supportedComponent().getName(), deleter);

		}
	}

	/**
	 * Alias for {@link #deleteEntity(Entity)}.
	 * 
	 * @param entityId
	 *            The id of the entity which should be deleted.
	 */
	public void deleteEntity(long entityId) {
		final Entity entity = entityService.getEntity(entityId);
		deleteEntity(entity);
	}

	/**
	 * Deletes the entity and removes all components from the system.
	 * 
	 * @param entity
	 *            The entity to remove from the system.
	 */
	public void deleteEntity(Entity entity) {

		Objects.requireNonNull(entity);

		LOG.debug("Deleting entity: {}", entity);

		final Collection<Component> components = entityService.getAllComponents(entity);

		// Iterate over all components and remove them.
		for (Component component : components) {
			deleteComponent(entity, component);
		}

		// Send message to kill off entity actor.
		akkaApi.sendEntityActor(entity.getId(), PoisonPill.getInstance());

		// Delete entity and stash it for later re-use.
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

		Objects.requireNonNull(entity);
		Objects.requireNonNull(clazz);

		LOG.debug("Deleting component: {} from entity: {}.", clazz.getSimpleName(), entity);

		final Component comp = entityService.getComponent(entity, clazz)
				.orElseThrow(IllegalArgumentException::new);

		deleteComponent(entity, comp);
	}

	private void deleteComponent(Entity entity, Component comp) {

		final String clazzname = comp.getClass().getName();

		if (componentCleaner.containsKey(clazzname)) {
			componentCleaner.get(clazzname).freeComponent(comp);
		}

		entityService.deleteComponent(entity, comp);

		cache.stashComponente(comp);
	}
}
