package net.bestia.zoneserver.service;

import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.hazelcast.core.HazelcastInstance;

import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.TestConfiguration;
import net.bestia.zoneserver.service.PlayerBestiaService;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes={TestConfiguration.class})
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
