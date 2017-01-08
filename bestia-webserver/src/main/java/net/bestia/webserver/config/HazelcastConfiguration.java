package net.bestia.webserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;

/**
 * In order to prevent Spring from automatically starting a hazelcast instance.
 * We need a client mode here since we dont want any operation to be peroformed
 * on the webserver.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
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
