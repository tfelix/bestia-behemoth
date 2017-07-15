package net.bestia.zoneserver;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.test.TestHazelcastInstanceFactory;

/**
 * Configures the app context for testing operations.
 * @author Thomas Felix
 *
 */
@SpringBootConfiguration
public class TestZoneConfiguration {
	
	@Bean
	@Primary
	public HazelcastInstance hazelcastMock() {
		TestHazelcastInstanceFactory hzFact = new TestHazelcastInstanceFactory();
		HazelcastInstance hz = hzFact.newHazelcastInstance();
		return hz;
	}

}
