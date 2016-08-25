package net.bestia.zoneserver.configuration;

import java.net.UnknownHostException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.HazelcastInstance;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.Cluster;
import net.bestia.server.AkkaCluster;
import net.bestia.server.BestiaActorContext;
import net.bestia.server.service.ClusterConfigurationService;
import net.bestia.zoneserver.actor.ZoneActor;
import net.bestia.zoneserver.service.ServerConfiguration;

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
public class AkkaComponent {

	private static final Logger LOG = LoggerFactory.getLogger(AkkaComponent.class);

	private static final String AKKA_CONFIG_NAME = "akka";

	@Bean
	public ActorSystem actorSystem(ServerConfiguration config, 
			HazelcastInstance hzInstance,
			ApplicationContext appContext)
			throws UnknownHostException {
		
		final Config akkaConfig = ConfigFactory.load(AKKA_CONFIG_NAME);

		final ActorSystem system = ActorSystem.create(AkkaCluster.CLUSTER_NAME, akkaConfig);
		
		// initialize the application context in the Akka Spring extension.
		//SpringExtension.SpringExtProvider.get(system).initialize(appContext);

		final ClusterConfigurationService clusterConfig = new ClusterConfigurationService(hzInstance);

		final Address selfAddr = Cluster.get(system).selfAddress();

		if (clusterConfig.shoudlJoinAsSeedNode()) {

			// Check if there are at least some seeds or if we need to bootstrap
			// the cluster.
			final List<Address> seedNodes = clusterConfig.getClusterNodes();

			if (seedNodes.size() == 0) {
				// Join as seed node since desired number of seeds is not
				// reached.
				Cluster.get(system).join(selfAddr);
			} else {
				Cluster.get(system).joinSeedNodes(seedNodes);
			}

		} else {
			// Only join as normal node.
			final List<Address> clusterNodes = clusterConfig.getClusterNodes();
			Cluster.get(system).joinSeedNodes(clusterNodes);
		}

		LOG.info("Zoneserver Akka Address: {}", selfAddr);

		// Save the new generated address. Must be done here since we MIGHT
		// could use random port join which will alter the port defined in
		// selfAddr.
		clusterConfig.addClusterNode(selfAddr);

		LOG.info("Starting actor tree.");
		final BestiaActorContext ctx = new BestiaActorContext(appContext);
		system.actorOf(ZoneActor.props(ctx), "behemoth");

		return system;
	}
}
