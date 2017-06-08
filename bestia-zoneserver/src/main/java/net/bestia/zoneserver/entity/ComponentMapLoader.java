package net.bestia.zoneserver.entity;

import java.util.Collection;
import java.util.Map;

import com.hazelcast.core.MapLoader;

import net.bestia.zoneserver.entity.component.Component;

/**
 * Specialized class which enables a component to be loaded from a permanent
 * database into the hazelcast in memory cache.
 * 
 * @author Thomas Felix
 *
 */
public class ComponentMapLoader implements MapLoader<Long, Component> {

	@Override
	public synchronized Component load(Long id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized Map<Long, Component> loadAll(Collection<Long> components) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized Iterable<Long> loadAllKeys() {
		// TODO Auto-generated method stub
		return null;
	}

}
