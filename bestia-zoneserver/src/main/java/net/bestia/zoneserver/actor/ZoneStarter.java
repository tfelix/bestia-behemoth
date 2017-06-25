package net.bestia.zoneserver.actor;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import net.bestia.zoneserver.actor.entity.EntityDeleteWorker;
import net.bestia.zoneserver.actor.entity.EntityWorker;
import net.bestia.zoneserver.actor.map.MapGeneratorClientActor;
import net.bestia.zoneserver.actor.map.MapGeneratorMasterActor;
import net.bestia.zoneserver.actor.zone.ActiveClientUpdateActor;
import net.bestia.zoneserver.actor.zone.IngestActor;
import net.bestia.zoneserver.actor.zone.InitGlobalActor;
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

	private final ZoneAkkaApi akkaApi;
	private final ActorSystem system;

	@Autowired
	public ZoneStarter(ActorSystem system, ZoneAkkaApi akkaApi) {

		this.akkaApi = Objects.requireNonNull(akkaApi);
		this.system = Objects.requireNonNull(system);
	}

	@Override
	public void run(String... strings) throws Exception {
		LOG.info("Starting actor system...");

		akkaApi.startActor(IngestActor.class);
		akkaApi.startActor(SendClientActor.class);
		akkaApi.startActor(ActiveClientUpdateActor.class);

		// Entity
		akkaApi.startActor(EntityWorker.class);
		akkaApi.startActor(EntityDeleteWorker.class);
		
		// Maintenance actors.
		akkaApi.startActor(MapGeneratorMasterActor.class);
		akkaApi.startActor(MapGeneratorClientActor.class);

		// Setup the init actor singelton for creation of the system.
		LOG.info("Starting the global init singeltons.");
		final ClusterSingletonManagerSettings settings = ClusterSingletonManagerSettings.create(system);
		final Props globalInitProps = SpringExtension.PROVIDER.get(system).props(InitGlobalActor.class);
		Props clusterProbs = ClusterSingletonManager.props(globalInitProps, PoisonPill.getInstance(), settings);
		system.actorOf(clusterProbs, "globalInit");
	}
}
