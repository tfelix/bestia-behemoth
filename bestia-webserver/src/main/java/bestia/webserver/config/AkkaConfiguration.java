package bestia.webserver.config;

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
import bestia.webserver.actor.ClusterConnectActor;
import bestia.webserver.actor.WebserverActorApi;
import bestia.webserver.actor.WebserverActorApiActor;
import bestia.webserver.service.ConfigurationService;

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
	public ActorSystem actorSystem(Config akkaConfig,
			ConfigurationService serverConfig) {

		LOG.debug("Starting actor system.");
		final ActorSystem system = ActorSystem.create("webserver", akkaConfig);

		LOG.debug("Starting webserver root actor.");
		rootActor = system.actorOf(ClusterConnectActor.props(serverConfig).withDeploy(Deploy.local()), "clusterUplink");

		return system;
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
										return new WebserverActorApiActor(rootActor);
									}

								}).withDeploy(Deploy.local()),
						"loginWebActor");

		return login;
	}
}
