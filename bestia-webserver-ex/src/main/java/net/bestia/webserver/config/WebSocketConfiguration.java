package net.bestia.webserver.config;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import net.bestia.webserver.websocket.BestiaSocketHandler;

/**
 * Configures the websocket connection endpoint.
 * 
 * @author Thomas Felix
 *
 */
@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

	private final BestiaSocketHandler socketHandler;

	@Autowired
	public WebSocketConfiguration(BestiaSocketHandler socketHandler) {

		this.socketHandler = Objects.requireNonNull(socketHandler);
	}

	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(socketHandler, "/socket")
				.setAllowedOrigins("*")
				.withSockJS();
	}
}
