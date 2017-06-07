package net.bestia.zoneserver.entity;

import java.util.Collection;
import java.util.Map;

import com.hazelcast.core.MapStore;

/**
 * Specialized class which enables the entities to be persistedly stored into a
 * persistent database. This is needed if the bestia server goes down or is shut
 * down so the world keeps persisted and can be reloaded.
 * 
 * @author Thomas Felix
 *
 */
public class EntityMapStore implements MapStore<Long, Entity> {

	@Override
	public synchronized Entity load(Long arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized Map<Long, Entity> loadAll(Collection<Long> arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized Iterable<Long> loadAllKeys() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized void delete(Long arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void deleteAll(Collection<Long> arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void store(Long arg0, Entity arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void storeAll(Map<Long, Entity> arg0) {
		// TODO Auto-generated method stub

	}

}
