package net.bestia.zoneserver.component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.hazelcast.core.HazelcastInstance;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.Cluster;
import net.bestia.model.dao.AccountDAO;
import net.bestia.server.ClusterConfigurationService;
import net.bestia.zoneserver.actor.LoginActor;
import net.bestia.zoneserver.service.ConfigurationService;

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
	private static final String ACTOR_SYSTEM_NAME = "BehemothCluster";
	
	private AccountDAO accountDao;
	
	@Autowired
	public void setAccountDao(AccountDAO accountDao) {
		this.accountDao = accountDao;
	}

	@Bean
	public ActorSystem actorSystem(Config akkaConfig, ConfigurationService config, HazelcastInstance hzInstance)
			throws UnknownHostException {

		final ActorSystem system = ActorSystem.create(ACTOR_SYSTEM_NAME, akkaConfig);

		final ClusterConfigurationService clusterConfig = new ClusterConfigurationService(hzInstance);

		if (clusterConfig.shoudlJoinAsSeedNode()) {

			// Check if there are at least some seeds or if we need to bootstrap
			// the cluster.
			final List<Address> seedNodes = clusterConfig.getClusterNodes();
			
			if(seedNodes.size() == 0) {
				// Join as seed node since desired number of seeds is not reached.
				final String hostAddr = InetAddress.getLocalHost().getHostAddress();
				final Address selfAddr = Address.apply("akka.tcp", ACTOR_SYSTEM_NAME, hostAddr, config.getServerPort());
				Cluster.get(system).join(selfAddr);
			} else {
				Cluster.get(system).joinSeedNodes(seedNodes);
			}
			
		} else {
			// Only joind as normal node.
			final List<Address> clusterNodes = clusterConfig.getClusterNodes();
			Cluster.get(system).joinSeedNodes(clusterNodes);
		}

		final Address myAddress = Cluster.get(system).selfAddress();
		LOG.info("Zoneserver Akka Address: {}", myAddress);

		// Save the new generated address. Must be done here since we MIGHT
		// could use random port join which will alter the port defined in
		// selfAddr.
		clusterConfig.addClusterNode(myAddress);
		
		startActors(system);

		return system;
	}
	
	/**
	 * Start the basic actor tree for beginning zone operation.
	 */
	private void startActors(ActorSystem system) {
		LOG.info("Starting actor tree.");
		system.actorOf(LoginActor.props(accountDao), "login");
		
	}


	@Bean
	public Config config() {
		final Config config = ConfigFactory.load(AKKA_CONFIG_NAME);
		return config;
	}
}
