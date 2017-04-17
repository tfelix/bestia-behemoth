package net.bestia.zoneserver.entity.ecs;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IdGenerator;

import net.bestia.zoneserver.entity.ecs.components.Component;

/**
 * This service is responsible for adding and removing components from entities.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class ComponentService {

	private static final Logger LOG = LoggerFactory.getLogger(Component.class);

	private static final String COMP_MAP = "components";
	private static final String COMP_ID = "components.id";

	private final IMap<Long, Component> components;
	private final IdGenerator idGenerator;
	private final EcsEntityService entityService;

	@Autowired
	public ComponentService(HazelcastInstance hz, EcsEntityService entityService) {

		this.components = Objects.requireNonNull(hz).getMap(COMP_MAP);
		this.entityService = Objects.requireNonNull(entityService);
		this.idGenerator = hz.getIdGenerator(COMP_ID);
	}

	public <T extends Component> Optional<T> getComponent(long entityId, Class<T> clazz) {

		final Entity e = entityService.getEntity(entityId);

		if (e == null) {
			return Optional.empty();
		}

		@SuppressWarnings("unchecked")
		final long compId = e.getComponentId((Class<Component>) clazz);

		if (compId == 0) {
			return Optional.empty();
		}

		final Component comp = components.get(compId);

		if (comp == null || !comp.getClass().isAssignableFrom(clazz)) {
			return Optional.empty();
		}

		return Optional.of(clazz.cast(comp));
	}

	/**
	 * A new component will be created and added to the entity. All components
	 * must have a constructor which only accepts a long value as an id.
	 * 
	 * @param entityId
	 * @param clazz
	 * @return
	 */
	public <T extends Component> T addComponent(long entityId, Class<T> clazz) {
		if (!Component.class.isAssignableFrom(clazz)) {
			throw new IllegalArgumentException("Only accept component classes.");
		}

		final Entity e = entityService.getEntity(entityId);

		if (e == null) {
			throw new IllegalArgumentException("Entity was not found.");
		}

		try {
			@SuppressWarnings("unchecked")
			Constructor<Component> ctor = (Constructor<Component>) clazz.getConstructor(Long.class);
			final Component comp = ctor.newInstance(getId());

			// Add component to entity and to the comp map.
			components.put(comp.getId(), comp);
			e.addComponent(comp);
			entityService.save(e);
			return clazz.cast(comp);

		} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException ex) {
			LOG.error("Could not instantiate component.", ex);
			throw new IllegalArgumentException(ex);
		}
	}

	/**
	 * Updates the given component back into the database.
	 * 
	 * @param component
	 *            The component to be updated into the database.
	 */
	public void update(Component component) {
		Objects.requireNonNull(component);
		components.put(component.getId(), component);
	}

	/**
	 * @return Non 0 new id of a component.
	 */
	private long getId() {
		final long id = idGenerator.newId();
		if (id == 0) {
			return getId();
		} else {
			return id;
		}
	}

	public void removeAllComponents(Entity entity) {
		entity.getComponentIds().forEach(components::removeAsync);
	}
}
