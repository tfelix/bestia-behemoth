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

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.actor.Deploy;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.cluster.Cluster;
import akka.japi.Creator;
import net.bestia.server.AkkaCluster;
import net.bestia.server.DiscoveryService;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.actor.ZoneAkkaApiActor;
import net.bestia.zoneserver.entity.EntityAkkaContext;
import net.bestia.zoneserver.entity.EntityContext;

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

	private static final Logger LOG = LoggerFactory.getLogger(AkkaConfiguration.class);

	private static final String AKKA_CONFIG_NAME = "akka";

	@Bean
	public ActorSystem actorSystem(HazelcastInstance hzInstance, ApplicationContext appContext)
			throws UnknownHostException {

		final Config akkaConfig = ConfigFactory.load(AKKA_CONFIG_NAME);
		final ActorSystem system = ActorSystem.create(AkkaCluster.CLUSTER_NAME, akkaConfig);

		// initialize the application context in the Akka Spring extension.
		SpringExtension.PROVIDER.get(system).initialize(appContext);

		final DiscoveryService clusterConfig = new DiscoveryService(hzInstance);

		final Address selfAddr = Cluster.get(system).selfAddress();
		final List<Address> seedNodes = clusterConfig.getClusterSeedNodes();

		if (clusterConfig.shoudlJoinAsSeedNode()) {

			// Check if there are at least some seeds or if we need to bootstrap
			// the cluster.
			if (seedNodes.size() == 0) {
				// Join as seed node since desired number of seeds is not
				// reached.
				Cluster.get(system).join(selfAddr);
			} else {
				Cluster.get(system).joinSeedNodes(seedNodes);
			}

		} else {
			// Only join as normal node.
			Cluster.get(system).joinSeedNodes(seedNodes);
		}

		LOG.info("Zoneserver Akka Address: {}", selfAddr);

		// Save the new generated address. Must be done here since we MIGHT
		// could use random port join which will alter the port defined in
		// selfAddr.
		clusterConfig.addClusterNode(selfAddr);

		return system;
	}

	/**
	 * Returns the {@link EntityAkkaContext} which is used by the entities
	 * itself to communicate back into the bestia system.
	 * 
	 * @param system
	 *            The current {@link ActorSystem}.
	 * @return The {@link EntityAkkaContext}.
	 */
	@Bean
	EntityContext entityContext(ActorSystem system) {
		
		final EntityContext ctx = TypedActor.get(system)
				.typedActorOf(new TypedProps<EntityAkkaContext>(EntityContext.class, EntityAkkaContext.class));

		return ctx;
	}
	
	@Bean
	public ZoneAkkaApi webserverLogin(ActorSystem system, ActorRef uplinkRouter) {

		final ZoneAkkaApi api = TypedActor.get(system)
				.typedActorOf(
						new TypedProps<ZoneAkkaApiActor>(ZoneAkkaApi.class, new Creator<ZoneAkkaApiActor>() {
							private static final long serialVersionUID = 1L;

							@Override
							public ZoneAkkaApiActor create() throws Exception {
								return new ZoneAkkaApiActor();
							}
						}).withDeploy(Deploy.local()), "internalZoneApi");

		return api;
	}
}
