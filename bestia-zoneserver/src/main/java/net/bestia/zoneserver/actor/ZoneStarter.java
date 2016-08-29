package net.bestia.zoneserver.actor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import akka.actor.ActorSystem;
import akka.actor.Props;
import net.bestia.zoneserver.actor.zone.ZoneActor;

/**
 * Starts the actor system to process bestia messages.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
public class ZoneStarter implements CommandLineRunner {

	private static final Logger LOG = LoggerFactory.getLogger(ZoneStarter.class);

	private ActorSystem system;

	@Autowired
	public void setSystem(ActorSystem system) {
		this.system = system;
	}

	@Override
	public void run(String... strings) throws Exception {
		LOG.info("Starting actor system...");

		final Props props = SpringExtension.Provider.get(system).props(ZoneActor.class);
		system.actorOf(props, "behemoth");
	}
}
