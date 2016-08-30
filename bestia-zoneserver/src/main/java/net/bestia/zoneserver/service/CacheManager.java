package net.bestia.zoneserver.service;

import java.util.Map;
import java.util.Objects;

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

	/**
	 * Ctor. Creates a generic cache manager which will handle map like mapping
	 * of values.
	 * 
	 * @param cacheKey
	 *            The unique cache key of this item set.
	 * @param cache
	 *            The cache instance used to store and retrive the objects.
	 */
	public CacheManager(String cacheKey, HazelcastInstance cache) {

		this.cacheKey = Objects.requireNonNull(cacheKey);
		this.cache = Objects.requireNonNull(cache);
	}

	/**
	 * Returns the value V for the given key K.
	 * 
	 * @param key
	 *            The key to retrive.
	 * @return The object stored under this key, or null.
	 */
	public V get(K key) {
		final Map<K, V> objects = cache.getMap(cacheKey);
		return objects.get(key);
	}

	/**
	 * Removes a object with the key K.
	 * 
	 * @param key
	 *            The key under which the object will get removed.
	 */
	public void remove(K key) {
		final Map<K, V> objects = cache.getMap(cacheKey);
		objects.remove(key);
	}

	/**
	 * Sets a new object with the key K.
	 * 
	 * @param key
	 *            The key to save the object.
	 * @param value
	 *            The value to be saved under the key K.
	 */
	public void set(K key, V value) {
		final Map<K, V> objects = cache.getMap(cacheKey);
		objects.put(key, value);
	}
}
