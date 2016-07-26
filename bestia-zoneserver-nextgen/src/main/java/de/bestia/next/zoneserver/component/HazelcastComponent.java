package de.bestia.next.zoneserver.component;

import javax.annotation.PreDestroy;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

@Component
public class HazelcastComponent {

	@Bean
	public HazelcastInstance getHazelcastInstance() {
		final HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance();
		
		return hazelcastInstance;
	}
	

	/**
	 * Safely shutdown the hazelcust cluster.
	 * 
	 * @throws Exception
	 */
	@PreDestroy
	public void cleanUp() throws Exception {

		//hzInstance.shutdown();

	}

}
