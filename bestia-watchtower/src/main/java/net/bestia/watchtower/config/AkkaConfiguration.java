package net.bestia.watchtower.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.HazelcastInstance;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorPath;
import akka.actor.ActorPaths;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.cluster.client.ClusterClient;
import akka.cluster.client.ClusterClientSettings;
import akka.japi.Creator;
import akka.routing.FromConfig;

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

	// Das hier noch verbessern.
	Set<ActorPath> initialContacts() {
		return new HashSet<ActorPath>(Arrays.asList(
				ActorPaths.fromString("akka.tcp://OtherSys@host1:2552/system/receptionist"),
				ActorPaths.fromString("akka.tcp://OtherSys@host2:2552/system/receptionist")));
	}

	@Bean
	public ActorSystem actorSystem(Config akkaConfig, HazelcastInstance hzClient) {

		final ActorSystem system = ActorSystem.create("watchtower", akkaConfig);

		final ClusterClientSettings clientSettings = ClusterClientSettings.create(system)
				.withInitialContacts(initialContacts());
		final Props clientProps = ClusterClient.props(clientSettings);

		final ActorRef clientActor = system.actorOf(clientProps, "client");
		clientActor.tell(new ClusterClient.Send("/user/serviceA", "hello", true), ActorRef.noSender());
		clientActor.tell(new ClusterClient.SendToAll("/user/serviceB", "hi"), ActorRef.noSender());

		return system;
	}

	/**
	 * Router to send messages to the bestia system.
	 *
	 */
	@Bean
	public ActorRef uplinkRouter(ActorSystem system) {

		ActorRef msgRouter = system.actorOf(FromConfig.getInstance().props(), "uplink");
		LOG.info("Message ingest path: {}", msgRouter.path().toString());

		return msgRouter;
	}

	@Bean
	public Config config() {
		final Config config = ConfigFactory.load(AKKA_CONFIG_NAME);
		return config;
	}
}
