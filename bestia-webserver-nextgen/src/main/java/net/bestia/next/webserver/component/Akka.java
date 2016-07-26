package net.bestia.next.webserver.component;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;

/**
 * Generates the akka configuration file which is used to connect to the remote
 * actor system of the bestia zone server. It overwrites the default akka config
 * file "application.conf" since this is already used by spring with
 * "akka.config".
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
public class Akka {
	
	private static final String AKKA_CONFIG_NAME = "akka";
	
	@Bean
	public ActorSystem actorSystem(Config akkaConfig, String serverName) {
		
		final ActorSystem system = ActorSystem.create(serverName, akkaConfig);
		
		return system;
	}
	
	@Bean
	public ActorSelection remoteReceiver(ActorSystem system) {
		// pattern: akka.<protocol>://<actorsystemname>@<hostname>:<port>/<actor path>
		return system.actorSelection("akka.tcp://app@10.0.0.1:2552/user/serviceA/worker");
	}

	/**
	 * TODO Config muss noch automatisch mit PORT und PFAD angepasst werden.
	 * Aktuell statisch.
	 * 
	 * @return
	 */
	@Bean
	public Config config() {
		final Config config = ConfigFactory.load(AKKA_CONFIG_NAME);
		return config;
	}
}
