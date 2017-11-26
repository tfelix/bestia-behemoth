package net.bestia.zoneserver.configuration;

import java.net.UnknownHostException;

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

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.cluster.Cluster;
import net.bestia.messages.MessageApi;
import net.bestia.zoneserver.actor.AkkaMessageApi;
import net.bestia.zoneserver.actor.BestiaRootActor;
import net.bestia.zoneserver.actor.SpringExtension;

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
	public ActorSystem actorSystem(ApplicationContext appContext)
			throws UnknownHostException {

		final Config akkaConfig = ConfigFactory.load(AKKA_CONFIG_NAME);
		LOG.debug("Loaded akka config: {}.", akkaConfig.toString());

		systemInstance = ActorSystem.create("BestiaBehemoth", akkaConfig);
		Address addr = new Address("tcp", "BestiaBehemoth", "localhost", 6767);
		Cluster.get(systemInstance).join(addr);

		// initialize the application context in the Akka Spring extension.
		SpringExtension.initialize(systemInstance, appContext);

		return systemInstance;
	}

	@Bean
	@Primary
	public MessageApi messageApi(ActorSystem system) {

		final TypedProps<AkkaMessageApi> typedProps = new TypedProps<>(MessageApi.class, AkkaMessageApi.class);
		final MessageApi msgApi = TypedActor.get(system).typedActorOf(typedProps, "akkaMsgApi");

		return msgApi;
	}
	
	@Bean
	public ActorRef rootActor(MessageApi msgApi) {
		LOG.info("Starting actor system...");

		final ActorRef rootActor = SpringExtension.actorOf(systemInstance, BestiaRootActor.class, msgApi);

		LOG.info("Bestia Zone startup completed.");
		
		return rootActor;
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
