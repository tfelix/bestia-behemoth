package net.bestia.memoryserver.persistance;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import com.hazelcast.core.MapStore;

import net.bestia.entity.component.Component;

/**
 * Specialized class which enables the components to be persistedly stored into
 * a persistent database. This is needed if the bestia server goes down or is
 * shut down so the world keeps persisted and can be reloaded.
 * 
 * @author Thomas Felix
 *
 */
@org.springframework.stereotype.Component
public class ComponentMapStore implements MapStore<Long, Component> {

	private final static Logger LOG = LoggerFactory.getLogger(ComponentMapStore.class);

	/**
	 * Must dep-inject via setter cause hazelcast wants no arg ctor.
	 */
	private final ComponentPersistService persistService;

	/**
	 * Zero Arg ctor needed by Hazelcast.
	 */
	@Autowired
	public ComponentMapStore(@Lazy ComponentPersistService persistService) {
		
		this.persistService = Objects.requireNonNull(persistService);
	}

	@Override
	public synchronized Component load(Long id) {
		LOG.trace("Loading component: {}.", id);

		final Component comp = persistService.load(id);
		return comp;
	}

	@Override
	public synchronized Map<Long, Component> loadAll(Collection<Long> ids) {

		final Map<Long, Component> components = new HashMap<>();

		for (Long id : ids) {
			final Component comp = persistService.load(id);
			components.put(id, comp);
		}

		return components;
	}

	@Override
	public synchronized Iterable<Long> loadAllKeys() {
		// All loading not supported at the moment since we can not easily
		// iterate over the SQL keys. Maybe implement later when there is more
		// time.
		return null;
	}

	@Override
	public synchronized void delete(Long id) {

		LOG.trace("Deleting component: {}.", id);

		persistService.delete(id);
	}

	@Override
	public synchronized void deleteAll(Collection<Long> ids) {

		LOG.trace("Deleting all {} components.", ids.size());

		for (Long id : ids) {
			persistService.delete(id);
		}
	}

	@Override
	public synchronized void store(Long id, Component comp) {

		LOG.trace("Store component: {}", comp);

		persistService.store(comp);
	}

	@Override
	public synchronized void storeAll(Map<Long, Component> components) {

		LOG.trace("Store all {} components.", components.size());

		for (Component comp : components.values()) {
			persistService.store(comp);
		}
	}

}
