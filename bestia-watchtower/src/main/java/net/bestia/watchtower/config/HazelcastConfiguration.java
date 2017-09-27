package net.bestia.watchtower.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;

/**
 * In order to prevent Spring from automatically starting a hazelcast instance.
 * We need a client mode here since we dont want any operation to be performed
 * on the webserver.
 * 
 * @author Thomas Felix
 *
 */
@Configuration
public class HazelcastConfiguration {

	/**
	 * Tries to create a Hazelcast client connection instance.
	 */
	@Bean
	public HazelcastInstance getHazelcastInstance() {
		final HazelcastInstance hz = HazelcastClient.newHazelcastClient();
		return hz;
	}
}
