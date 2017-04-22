package net.bestia.zoneserver.actor;

import java.lang.reflect.Field;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import net.bestia.zoneserver.actor.SpringExtension.SpringExt;
import net.bestia.zoneserver.actor.zone.IngestActor;
import net.bestia.zoneserver.actor.zone.SendClientActor;

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
	private final SpringExt springExt;

	@Autowired
	public ZoneStarter(ActorSystem system) {

		this.system = Objects.requireNonNull(system);

		// Spawn the root actor of the system. Bootstrapping via spring actor
		// because of automatic injections. We must do it manually here because
		// we are not inside an actor and have no access to a
		// UntypedActorContext.
		this.springExt = SpringExtension.PROVIDER.get(system);
	}

	@Override
	public void run(String... strings) throws Exception {
		LOG.info("Starting actor system...");
		
		startActor(IngestActor.class);
		startActor(SendClientActor.class);
	}

	private ActorRef startActor(Class<? extends Actor> actorClazz) {
		
		Props props = springExt.props(actorClazz);
		ActorRef actor;
		try {
			Field f = actorClazz.getField("NAME");
			if (f.getType() == String.class) {
				String name = (String) f.get(null);
				actor = system.actorOf(props, name);
			}

			actor = system.actorOf(props);

		} catch (Exception e) {
			actor =  system.actorOf(props);
		}
		
		LOG.info("Starting actor: {}, path: {}", actorClazz, actor.path().toString());
		
		return actor;
	}
	
}
