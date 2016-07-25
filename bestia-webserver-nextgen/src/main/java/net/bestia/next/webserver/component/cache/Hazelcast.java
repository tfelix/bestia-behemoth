package net.bestia.next.webserver.component.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;

@Component
public class Hazelcast {

	@Bean
	public ClientConfig clientConfig() {
		final ClientConfig clientConfig = new ClientConfig();

		return clientConfig;
	}

	@Bean
	public HazelcastInstance client(ClientConfig clientConfig) {
		final HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
		
		return client;
	}

}
