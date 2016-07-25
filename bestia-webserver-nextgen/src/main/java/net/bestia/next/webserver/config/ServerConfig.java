package net.bestia.next.webserver.config;

import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class ServerConfig {

	/**
	 * Generates a unique name for this bestia webserver.
	 * 
	 * @return Unique name.
	 */
	@Bean
	public String serverName() {
		final String name = String.format("bestia-webserver-%s", UUID.randomUUID().toString());
		return name;
	}

}
