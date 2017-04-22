package net.bestia.webserver.config;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import net.bestia.webserver.actor.WebserverActorApi;
import net.bestia.webserver.websocket.BestiaSocketHandler;

/**
 * Configures the websocket connection endpoint.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

	private final WebserverActorApi actorApi;

	@Autowired
	public WebSocketConfiguration(WebserverActorApi actorApi) {

		this.actorApi = Objects.requireNonNull(actorApi);
	}

	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(new BestiaSocketHandler(actorApi), "/socket")
				.setAllowedOrigins("*")
				.withSockJS();
	}
}
