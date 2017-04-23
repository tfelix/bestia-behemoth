package net.bestia.zoneserver;

import net.bestia.zoneserver.actor.ZoneAkkaApi;
import static org.mockito.Mockito.*;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.test.TestHazelcastInstanceFactory;

@Configuration
public class BasicMocks {
	
	@Bean
	public HazelcastInstance hazelcast() {
		TestHazelcastInstanceFactory hzFact = new TestHazelcastInstanceFactory();
    	HazelcastInstance hz = hzFact.newHazelcastInstance();
    	return hz;
	}

	public ZoneAkkaApi zoneAkkaApi() {
		
		ZoneAkkaApi api = mock(ZoneAkkaApi.class);
		return api;
	}

}
