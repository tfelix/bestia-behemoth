package net.bestia.webserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import net.bestia.webserver.handler.BestiaSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(bestiaSocketHandler(), "/socket")
				.setAllowedOrigins("*")
				.withSockJS();
	}

	@Bean
	public BestiaSocketHandler bestiaSocketHandler() {
		return new BestiaSocketHandler();
	}
}
