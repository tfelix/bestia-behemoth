package de.bestia.next.zoneserver.component;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.hazelcast.core.HazelcastInstance;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.Cluster;
import de.bestia.next.zoneserver.service.ConfigurationService;
import net.bestia.next.service.ClusterConfigurationService;

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
public class AkkaComponent {

	private static final Logger LOG = LoggerFactory.getLogger(AkkaComponent.class);

	private static final String AKKA_CONFIG_NAME = "akka";
	private static final String ACTOR_SYSTEM_NAME = "BestiaZone";

	@Bean
	public ActorSystem actorSystem(Config akkaConfig, ConfigurationService config, HazelcastInstance hzInstance) {

		final ActorSystem system = ActorSystem.create(ACTOR_SYSTEM_NAME, akkaConfig);

		final ClusterConfigurationService clusterConfig = new ClusterConfigurationService(hzInstance);

		final List<Address> clusterNodes = clusterConfig.getClusterNodes();
		Cluster.get(system).joinSeedNodes(clusterNodes);

		final Address myAddress = Cluster.get(system).selfAddress();
		LOG.info("Zoneserver Akka Address: {}", myAddress);

		clusterConfig.addClusterNode(myAddress);

		return system;
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
