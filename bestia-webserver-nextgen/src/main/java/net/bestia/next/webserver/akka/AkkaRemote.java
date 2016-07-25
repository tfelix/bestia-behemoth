package net.bestia.next.webserver.akka;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.typesafe.config.Config;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;

@Component
public class AkkaRemote {
	
	@Bean
	public ActorSystem actorSystem(Config akkaConfig, String serverName) {
		
		final ActorSystem system = ActorSystem.create(serverName, akkaConfig);
		
		return system;
	}
	
	@Bean
	public ActorSelection remoteReceiver(ActorSystem system) {
		return system.actorSelection("akka.tcp://app@10.0.0.1:2552/user/serviceA/worker");
	}
}
