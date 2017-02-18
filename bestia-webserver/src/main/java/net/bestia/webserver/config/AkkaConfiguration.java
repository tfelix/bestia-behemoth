package net.bestia.webserver.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.HazelcastInstance;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.Cluster;
import net.bestia.server.AkkaCluster;
import net.bestia.server.DiscoveryService;
import net.bestia.webserver.actor.ActorSystemTerminator;
import net.bestia.webserver.actor.WebClusterListenerActor;

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

	private final static Logger LOG = LoggerFactory.getLogger(AkkaConfiguration.class);
	private final static String AKKA_CONFIG_NAME = "akka";

	private ActorSystemTerminator terminator = null;

	@Bean
	public ActorSystem actorSystem(Config akkaConfig, HazelcastInstance hzClient) {

		final ActorSystem system = ActorSystem.create(AkkaCluster.CLUSTER_NAME, akkaConfig);

		final DiscoveryService clusterConfig = new DiscoveryService(hzClient);
		final List<Address> seedNodes = clusterConfig.getClusterSeedNodes();

		if (seedNodes.isEmpty()) {
			LOG.error("No seed cluster nodes found. Can not join the system. Shutting down.");
			System.exit(1);
		}

		LOG.info("Attempting to joing the bestia cluster...");
		final Cluster cluster = Cluster.get(system);
		cluster.joinSeedNodes(seedNodes);

		// Prepare cleanup if we are removed from the cluster we will terminate.
		final ActorSystemTerminator terminator = systemTerminator(system, hzClient);
		cluster.registerOnMemberRemoved(terminator);

		// Subscribe for dead letter checking and domain events.
		system.actorOf(WebClusterListenerActor.props(terminator));

		return system;
	}

	/**
	 * Creates singelton terminator bean.
	 * 
	 * @param system The actor system.
	 * @param hz Hazelcast
	 * @return The singelton {@link ActorSystemTerminator}.
	 */
	@Bean
	public ActorSystemTerminator systemTerminator(ActorSystem system, HazelcastInstance hz) {
		if (terminator == null) {
			terminator = new ActorSystemTerminator(system, hz);
		}
		return terminator;
	}

	@Bean
	public Config config() {
		final Config config = ConfigFactory.load(AKKA_CONFIG_NAME);
		return config;
	}
}
