package net.bestia.webserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import net.bestia.webserver.websocket.BestiaSocketHandler;
import net.bestia.webserver.websocket.BestiaSocketInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(bestiaSocketHandler(), "/socket")
				.addInterceptors()
				.setAllowedOrigins("*")
				.withSockJS();
	}
	
	@Bean
	public BestiaSocketInterceptor bestiaSocketInterceptor() {
		return new BestiaSocketInterceptor();
	}

	@Bean
	public BestiaSocketHandler bestiaSocketHandler() {
		return new BestiaSocketHandler();
	}
}
