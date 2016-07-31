package net.bestia.zoneserver.service;

import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;

import com.hazelcast.core.HazelcastInstance;

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

	/**
	 * Sets the currently active bestia for this account.
	 * 
	 * @param accountId
	 * @param bestiaId
	 */
	public void setActive(K key, V value) {
		final Map<K, V> objects = cache.getMap(cacheKey);
		objects.put(key, value);
	}
}
