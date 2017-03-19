package net.bestia.zoneserver.actor;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import akka.actor.ActorSystem;
import net.bestia.zoneserver.actor.map.MapGeneratorClientActor;
import net.bestia.zoneserver.actor.map.MapGeneratorMasterActor;
import net.bestia.zoneserver.actor.zone.UplinkActor;

/**
 * Starts the actor system to process bestia messages.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
public class ZoneStarter implements CommandLineRunner {

	private static final Logger LOG = LoggerFactory.getLogger(ZoneStarter.class);

	private final ActorSystem system;

	@Autowired
	public ZoneStarter(ActorSystem system) {

		this.system = Objects.requireNonNull(system);

	}

	@Override
	public void run(String... strings) throws Exception {
		LOG.info("Starting actor system...");

		// Spawn the root actor of the system. Bootstrapping via spring actor
		// because of automatic injections.

		SpringExtension.actorOf(system, MapGeneratorClientActor.class);
		SpringExtension.actorOf(system, MapGeneratorMasterActor.class);
		SpringExtension.actorOf(system, UplinkActor.class);
	}
}
