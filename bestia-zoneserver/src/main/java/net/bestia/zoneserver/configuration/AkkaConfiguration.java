package net.bestia.zoneserver.configuration;

import java.net.UnknownHostException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.cluster.Cluster;
import net.bestia.server.AkkaCluster;
import net.bestia.server.DiscoveryService;
import net.bestia.zoneserver.actor.AkkaMessageApi;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.ZoneMessageApi;

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
	public ActorSystem actorSystem(DiscoveryService clusterConfig, ApplicationContext appContext)
			throws UnknownHostException {

		final Config akkaConfig = ConfigFactory.load(AKKA_CONFIG_NAME);

		systemInstance = ActorSystem.create(AkkaCluster.CLUSTER_NAME, akkaConfig);

		// initialize the application context in the Akka Spring extension.
		SpringExtension.initialize(systemInstance, appContext);

		final Address selfAddr = Cluster.get(systemInstance).selfAddress();
		final List<Address> seedNodes = clusterConfig.getClusterSeedNodes();

		LOG.info("Received live endpoints: {}", seedNodes);

		// Check if there are at least some seeds or if we need to bootstrap
		// the cluster.
		if (seedNodes.size() == 0) {
			// Join as seed node since desired number of seeds is not
			// reached.
			LOG.info("Joining as bootstrap seed node.");
			Cluster.get(systemInstance).join(selfAddr);
		} else {
			LOG.info("Joining as regular node.");
			Cluster.get(systemInstance).joinSeedNodes(seedNodes);
		}

		LOG.info("Zoneserver Akka Address is: {}", selfAddr);

		return systemInstance;
	}

	@Bean
	@Primary
	public ZoneMessageApi messageApi(ActorSystem system) {

		final TypedProps<AkkaMessageApi> typedProps = new TypedProps<>(ZoneMessageApi.class, AkkaMessageApi.class);
		final ZoneMessageApi msgApi = TypedActor.get(system).typedActorOf(typedProps, "akkaMsgApi");
		
		return msgApi;
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
