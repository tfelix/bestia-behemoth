package net.bestia.zoneserver.actor;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import net.bestia.zoneserver.actor.SpringExtension.SpringExt;
import net.bestia.zoneserver.actor.chat.ChatActor;
import net.bestia.zoneserver.actor.map.MapGeneratorClientActor;
import net.bestia.zoneserver.actor.uplink.UplinkActor;
import net.bestia.zoneserver.actor.uplink.UplinkLoginActor;
import net.bestia.zoneserver.actor.zone.EngineReadyActor;
import net.bestia.zoneserver.actor.zone.MessageRoutingActor;
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

	private final ActorSystem system;

	@Autowired
	public ZoneStarter(ActorSystem system) {

		this.system = Objects.requireNonNull(system);

	}

	@Override
	public void run(String... strings) throws Exception {
		LOG.info("Starting actor system...");

		// Spawn the root actor of the system. Bootstrapping via spring actor
		// because of automatic injections. We must do it manually here because
		// we are not inside an actor and have no access to a
		// UntypedActorContext.
		final SpringExt springExt = SpringExtension.PROVIDER.get(system);

		Props props = springExt.props(ZoneActor.class);
		ActorRef zone = system.actorOf(props, ZoneActor.NAME);
		LOG.warn("Path: {}", zone.path().toString());
		
		props = springExt.props(MapGeneratorClientActor.class);
		system.actorOf(props, MapGeneratorClientActor.NAME);
		
		props = springExt.props(UplinkActor.class);
		system.actorOf(props, UplinkActor.NAME);
		
		props = springExt.props(UplinkLoginActor.class);
		system.actorOf(props, UplinkLoginActor.NAME);
		
		// TESTING AREA FOR ALTERNATIVE ROUTING
		props = springExt.props(ChatActor.class);
		system.actorOf(props, ChatActor.NAME);
		
		props = springExt.props(EngineReadyActor.class);
		system.actorOf(props, EngineReadyActor.NAME);
		
		props = springExt.props(MessageRoutingActor.class);
		system.actorOf(props, MessageRoutingActor.NAME);
		
		// Testing.
		/*
		props = springExt.props(EchoActor.class).withRouter(new FromConfig());
		ActorRef router = system.actorOf(props, "echoRouter");
		
		for (int i = 0; i < 10; i++){
            router.tell("Test " + i, ActorRef.noSender());
        }*/
	}
}
