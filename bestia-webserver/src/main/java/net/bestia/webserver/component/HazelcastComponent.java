package net.bestia.webserver.component;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;

@Component
public class HazelcastComponent {

	@Bean
	public HazelcastInstance client() {
		final ClientConfig clientConfig = new ClientConfig();
		final HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
		return client;
	}

}
