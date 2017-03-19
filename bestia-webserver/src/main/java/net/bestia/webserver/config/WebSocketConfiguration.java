package net.bestia.webserver.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
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
	
	private ActorSystem actorSystem;
	private ActorRef uplinkRouter;
	
	@Autowired
	public WebSocketConfiguration(ActorSystem actorSystem, ActorRef uplinkRouter) {
		
		this.actorSystem = actorSystem;
		this.uplinkRouter = uplinkRouter;
	}

	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(new BestiaSocketHandler(actorSystem, uplinkRouter), "/socket")
				.setAllowedOrigins("*")
				.withSockJS();
	}
}
