package net.bestia.zoneserver.configuration;

import java.net.UnknownHostException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.hazelcast.core.HazelcastInstance;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

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
import net.bestia.zoneserver.actor.zone.ZoneAkkaApiActor;

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
@Profile("production")
public class AkkaConfiguration implements DisposableBean {

	private static final Logger LOG = LoggerFactory.getLogger(AkkaConfiguration.class);

	private static final String AKKA_CONFIG_NAME = "akka";

	private ActorSystem systemInstance;

	@Bean
	public ActorSystem actorSystem(HazelcastInstance hzInstance, ApplicationContext appContext)
			throws UnknownHostException {

		final Config akkaConfig = ConfigFactory.load(AKKA_CONFIG_NAME);

		systemInstance = ActorSystem.create(AkkaCluster.CLUSTER_NAME, akkaConfig);

		// initialize the application context in the Akka Spring extension.
		SpringExtension.PROVIDER.get(systemInstance).initialize(appContext);

		final DiscoveryService clusterConfig = new DiscoveryService(hzInstance);

		final Address selfAddr = Cluster.get(systemInstance).selfAddress();
		final List<Address> seedNodes = clusterConfig.getClusterSeedNodes();

		if (clusterConfig.shoudJoinAsSeedNode()) {

			// Check if there are at least some seeds or if we need to bootstrap
			// the cluster.
			if (seedNodes.size() == 0) {
				// Join as seed node since desired number of seeds is not
				// reached.
				Cluster.get(systemInstance).join(selfAddr);
			} else {
				Cluster.get(systemInstance).joinSeedNodes(seedNodes);
			}

		} else {
			// Only join as normal node.
			Cluster.get(systemInstance).joinSeedNodes(seedNodes);
		}

		LOG.info("Zoneserver Akka Address: {}", selfAddr);

		// Save the new generated address. Must be done here since we MIGHT
		// could use random port join which will alter the port defined in
		// selfAddr.
		clusterConfig.addClusterNode(selfAddr);

		return systemInstance;
	}

	@Bean
	public ZoneAkkaApi zoneAkkaApi(ActorSystem system) {

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

	/**
	 * Kill the akka instance if it has been created and Spring shuts down.
	 */
	@Override
	public void destroy() throws Exception {
		LOG.info("Stopping Akka instance.");

		if (systemInstance != null) {
			systemInstance.terminate();
		}

	}
}
