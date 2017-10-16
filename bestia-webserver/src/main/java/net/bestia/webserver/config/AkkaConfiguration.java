package net.bestia.webserver.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.HazelcastInstance;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.japi.Creator;
import akka.routing.FromConfig;
import net.bestia.server.AkkaCluster;
import net.bestia.webserver.actor.ActorSystemTerminator;
import net.bestia.webserver.actor.ClientConnectActor;
import net.bestia.webserver.actor.ClusterConnectActor;
import net.bestia.webserver.actor.ClusterConnectionListenerActor;
import net.bestia.webserver.actor.WebserverActorApi;
import net.bestia.webserver.actor.WebserverActorApiActor;
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

	private final static Logger LOG = LoggerFactory.getLogger(AkkaConfiguration.class);
	private final static String AKKA_CONFIG_NAME = "akka";

	private ActorSystemTerminator terminator = null;

	@Bean
	public ActorSystem actorSystem(Config akkaConfig,
			HazelcastInstance hzClient,
			ConfigurationService serverConfig) {

		LOG.debug("Starting actor system.");
		final ActorSystem system = ActorSystem.create("webserver", akkaConfig);

		//LOG.debug("Starting ClusterConnectActor.");
		//final Props clusterConnectProps = ClusterConnectActor.props(hzClient);
		//system.actorOf(clusterConnectProps, ClusterConnectActor.NAME);
		
		LOG.debug("Starting ClusterConnectActor.");
		Props props = Props.create(ClientConnectActor.class);
		system.actorOf(props, "connection");
		
		//LOG.debug("Starting ClusterConnectionListenerActor.");
		// Subscribe for dead letter checking and domain events.
		//final Props clusterListenerProps = ClusterConnectionListenerActor.props(serverConfig, hzClient);
		//system.actorOf(clusterListenerProps, "clusterListtener");

		return system;
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

	@Bean
	public WebserverActorApi webserverLogin(ActorSystem system) {

		final WebserverActorApi login = TypedActor.get(system)
				.typedActorOf(
						new TypedProps<WebserverActorApiActor>(WebserverActorApi.class,
								new Creator<WebserverActorApiActor>() {
									private static final long serialVersionUID = 1L;

									@Override
									public WebserverActorApiActor create() throws Exception {
										return new WebserverActorApiActor(null);
									}
								}).withDeploy(Deploy.local()),
						"loginWebActor");

		return login;
	}
}
