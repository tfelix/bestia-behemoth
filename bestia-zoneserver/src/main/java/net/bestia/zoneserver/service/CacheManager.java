package net.bestia.zoneserver.service;

import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;

import com.hazelcast.core.HazelcastInstance;

/**
 * This is a generic cache manager/helper. It can be used to save values inside
 * of the cache instance/memory db. This is needed so the actor dont store a
 * state which is not desirable inside a cloud environment.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 * @param <K>
 * @param <V>
 */
public class CacheManager<K, V> {

	private final String cacheKey;
	private final HazelcastInstance cache;

	@Autowired
	public CacheManager(String cacheKey, HazelcastInstance cache) {

		this.cacheKey = Objects.requireNonNull(cacheKey);
		this.cache = Objects.requireNonNull(cache);
	}

	public V get(K key) {
		final Map<K, V> objects = cache.getMap(cacheKey);
		return objects.get(key);
	}

	public void remove(K key) {
		final Map<K, V> objects = cache.getMap(cacheKey);
		objects.remove(key);
	}

	public void set(K key, V value) {
		final Map<K, V> objects = cache.getMap(cacheKey);
		objects.put(key, value);
	}
}
