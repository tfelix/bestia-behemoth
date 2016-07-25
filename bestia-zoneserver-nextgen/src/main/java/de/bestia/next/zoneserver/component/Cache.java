package de.bestia.next.zoneserver.component;

import javax.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import com.hazelcast.core.HazelcastInstance;

@Component
public class Cache {

	/**
	 * Safely shutdown the hazelcust cluster.
	 * 
	 * @throws Exception
	 */
	@PreDestroy
	public void cleanUp(HazelcastInstance hzInstance) throws Exception {

		hzInstance.shutdown();

	}
}
