package net.bestia.webserver.component;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.hazelcast.core.HazelcastInstance;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.actor.DeadLetter;
import akka.actor.Props;
import akka.cluster.Cluster;
import net.bestia.server.AkkaCluster;
import net.bestia.server.service.ClusterConfigurationService;
import net.bestia.webserver.actor.DeadLetterWatchActor;
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
@Component
public class AkkaComponent {

	private final static Logger LOG = LoggerFactory.getLogger(AkkaComponent.class);
	private final static String AKKA_CONFIG_NAME = "akka";

	@Bean
	public ActorSystem actorSystem(Config akkaConfig, ConfigurationService serverConfig, HazelcastInstance hzClient) {

		final ActorSystem system = ActorSystem.create(AkkaCluster.CLUSTER_NAME, akkaConfig);
		
		// Subscribe for dead letter checking.
		final ActorRef deadLetterActor = system.actorOf(Props.create(DeadLetterWatchActor.class));
		system.eventStream().subscribe(deadLetterActor, DeadLetter.class);

		final ClusterConfigurationService clusterConfig = new ClusterConfigurationService(hzClient);
		final List<Address> seedNodes = clusterConfig.getClusterNodes();
		
		if(seedNodes.isEmpty()) {
			LOG.error("No seed cluster nodes found. Can not join the system. Shutting down.");
			System.exit(1);
		}

		LOG.info("Attempting to joing the bestia cluster...");
		Cluster.get(system).joinSeedNodes(seedNodes);

		return system;
	}

	@Bean
	public Config config() {
		final Config config = ConfigFactory.load(AKKA_CONFIG_NAME);
		return config;
	}
}
