package net.bestia.zoneserver.entity;

import java.util.Collection;
import java.util.Map;

import com.hazelcast.core.MapStore;

import net.bestia.zoneserver.entity.component.Component;

/**
 * Specialized class which enables the components to be persistedly stored into
 * a persistent database. This is needed if the bestia server goes down or is
 * shut down so the world keeps persisted and can be reloaded.
 * 
 * @author Thomas Felix
 *
 */
public class ComponentMapStore implements MapStore<Long, Component> {

	@Override
	public synchronized Component load(Long arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized Map<Long, Component> loadAll(Collection<Long> arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized Iterable<Long> loadAllKeys() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized void delete(Long key) {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void deleteAll(Collection<Long> keys) {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void store(Long key, Component comp) {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void storeAll(Map<Long, Component> arg0) {
		// TODO Auto-generated method stub

	}

}
