package net.bestia.webserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import net.bestia.server.AkkaCluster;
import net.bestia.webserver.actor.ActorSystemTerminator;
import net.bestia.webserver.service.ConfigurationService;

/**
 * Generates the akka configuration file which is used to connect to the remote
 * actor system of the bestia zone server. It overwrites the default akka config
 * file "application.conf" since this is already used by spring with
 * "akka.config".
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Configuration
public class AkkaConfiguration {

	//private final static Logger LOG = LoggerFactory.getLogger(AkkaConfiguration.class);
	private final static String AKKA_CONFIG_NAME = "akka";

	private ActorSystemTerminator terminator = null;

	@Bean
	public ActorSystem actorSystem(Config akkaConfig,
			ConfigurationService serverConfig) {

		return ActorSystem.create(AkkaCluster.CLUSTER_NAME, akkaConfig);
	}

	/**
	 * Creates singelton terminator bean.
	 * 
	 * @param system
	 *            The actor system.
	 * @param hz
	 *            Hazelcast
	 * @return The singelton {@link ActorSystemTerminator}.
	 */
	@Bean
	public ActorSystemTerminator systemTerminator(ActorSystem system) {
		if (terminator == null) {
			terminator = new ActorSystemTerminator(system);
		}
		return terminator;
	}

	@Bean
	public Config config() {
		final Config config = ConfigFactory.load(AKKA_CONFIG_NAME);
		return config;
	}
}
