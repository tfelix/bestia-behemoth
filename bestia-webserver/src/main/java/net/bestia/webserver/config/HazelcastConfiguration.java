package net.bestia.webserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;

/**
 * In order to prevent Spring from automatically starting a 
 * @author Thomas
 *
 */
@Configuration
public class HazelcastConfiguration {
	
	@Bean
	public HazelcastInstance getHazelcastInstance() {
		HazelcastInstance hz = HazelcastClient.newHazelcastClient();
		return hz;
	}

}
