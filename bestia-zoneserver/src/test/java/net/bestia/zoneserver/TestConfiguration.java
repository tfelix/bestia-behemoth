package net.bestia.zoneserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.test.TestHazelcastInstanceFactory;

@Configuration
public class TestConfiguration {
	
	@Bean
	public HazelcastInstance hazelcast() {
		TestHazelcastInstanceFactory hzFact = new TestHazelcastInstanceFactory();
    	HazelcastInstance hz = hzFact.newHazelcastInstance();
    	return hz;
	}

}
