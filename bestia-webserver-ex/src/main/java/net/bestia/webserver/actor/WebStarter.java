package net.bestia.webserver.actor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import akka.actor.ActorPath;
import akka.actor.ActorPaths;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Initializes and setup the actor system.
 * 
 * @author Thomas Felix
 *
 */
@Component
public final class WebStarter implements CommandLineRunner {

	private static final Logger LOG = LoggerFactory.getLogger(WebStarter.class);

	private final ActorSystem system;

	private Set<ActorPath> initialContacts() {
		return new HashSet<ActorPath>(Arrays.asList(
				ActorPaths.fromString("akka.tcp://OtherSys@host1:2552/system/receptionist"),
				ActorPaths.fromString("akka.tcp://OtherSys@host2:2552/system/receptionist")));
	}

	@Autowired
	public WebStarter(ActorSystem system) {

		this.system = Objects.requireNonNull(system);
	}

	@Override
	public void run(String... args) throws Exception {
		LOG.info("Starting webserver actor system...");

		Props props = ClusterConnectionObserverActor.props(initialContacts());
		final ActorRef clusterObserverActor = system.actorOf(props, "cluster");
		
		props = ClientConnectionSupervisorActor.props(clusterObserverActor);
		system.actorOf(props, "clients");
	}

}
