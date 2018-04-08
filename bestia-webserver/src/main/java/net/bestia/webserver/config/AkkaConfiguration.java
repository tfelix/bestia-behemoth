package net.bestia.webserver.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Deploy;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.japi.Creator;
import net.bestia.webserver.actor.ClusterConnectActor;
import net.bestia.webserver.actor.WebserverActorApi;
import net.bestia.webserver.actor.WebserverActorApiActor;
import net.bestia.webserver.service.ConfigurationService;
import org.springframework.context.annotation.Profile;

/**
 * Generates the akka configuration file which is used to connect to the remote
 * actor system of the bestia zone server. It overwrites the default akka config
 * file "application.conf" since this is already used by spring with
 * "akka.config".
 * 
 * @author Thomas Felix
 *
 */
@Configuration
public class AkkaConfiguration {

	private final static Logger LOG = LoggerFactory.getLogger(AkkaConfiguration.class);
	private final static String AKKA_CONFIG_NAME = "akka";

	private ActorRef rootActor;

	@Bean
	public ActorSystem actorSystem(Config akkaConfig, ConfigurationService serverConfig) {

		LOG.debug("Starting actor system.");
		final ActorSystem system = ActorSystem.create("webserver", akkaConfig);

		LOG.debug("Starting webserver root actor.");
		rootActor = system.actorOf(ClusterConnectActor.props(serverConfig)
				.withDeploy(Deploy.local()), "clusterUplink");

		return system;
	}

	@Bean
	public Config config() {
		return ConfigFactory.load(AKKA_CONFIG_NAME);
	}

	@Bean
	public WebserverActorApi webserverLogin(ActorSystem system) {
		return TypedActor.get(system)
				.typedActorOf(
								new TypedProps<>(WebserverActorApi.class,
												new Creator<WebserverActorApiActor>() {
													private static final long serialVersionUID = 1L;

													@Override
													public WebserverActorApiActor create() {
														return new WebserverActorApiActor(rootActor);
													}

												}).withDeploy(Deploy.local()),
						"loginWebActor");
	}
}
