package net.bestia.next.webserver.akka;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

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
public class AkkaConfig {

	private static final String AKKA_CONFIG_NAME = "akka";

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
