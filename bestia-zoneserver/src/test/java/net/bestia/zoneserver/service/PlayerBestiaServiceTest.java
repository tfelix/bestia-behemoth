package net.bestia.zoneserver.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.hazelcast.core.HazelcastInstance;

import net.bestia.zoneserver.BasicMocks;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes={BasicMocks.class})
public class PlayerBestiaServiceTest {
	
	@Configuration
	static class ContextConfiguration {

		@Bean
		public LatencyService latencyService(HazelcastInstance hz) {
			LatencyService service = new LatencyService(hz);
			// set properties, etc.
			return service;
		}
	}
	
	

	@Test
	public void getAllBesitas_wrongId_null() {
		//final Set<PlayerBestia> result = service.getAllBestias(1337);
		//Assert.assertNull(result);
	}
}
