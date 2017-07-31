package net.bestia.zoneserver;

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
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.connection.ConnectionManagerActor;
import net.bestia.zoneserver.actor.entity.EntityManagerActor;
import net.bestia.zoneserver.actor.map.MapGeneratorClientActor;
import net.bestia.zoneserver.actor.map.MapGeneratorMasterActor;
import net.bestia.zoneserver.actor.rest.ChangePasswordActor;
import net.bestia.zoneserver.actor.rest.CheckUsernameDataActor;
import net.bestia.zoneserver.actor.rest.RequestLoginActor;
import net.bestia.zoneserver.actor.zone.ActiveClientUpdateActor;
import net.bestia.zoneserver.actor.zone.HeartbeatActor;
import net.bestia.zoneserver.actor.zone.IngestActor;
import net.bestia.zoneserver.actor.zone.IngestExActor;
import net.bestia.zoneserver.actor.zone.InitGlobalActor;
import net.bestia.zoneserver.actor.zone.SendActiveRangeActor;
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;
import net.bestia.zoneserver.actor.zone.ZoneClusterListenerActor;
import net.bestia.zoneserver.script.ScriptService;

/**
 * Starts the actor system to process bestia messages.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class ZoneStarter implements CommandLineRunner {

	private static final Logger LOG = LoggerFactory.getLogger(ZoneStarter.class);

	private final ZoneAkkaApi akkaApi;
	private final ActorSystem system;
	private final ScriptService scriptService;

	@Autowired
	public ZoneStarter(ActorSystem system, ZoneAkkaApi akkaApi, ScriptService scriptService) {

		this.akkaApi = Objects.requireNonNull(akkaApi);
		this.system = Objects.requireNonNull(system);
		this.scriptService = Objects.requireNonNull(scriptService);
	}

	@Override
	public void run(String... strings) throws Exception {
		LOG.info("Starting actor system...");

		akkaApi.startActor(IngestActor.class);
		akkaApi.startActor(IngestExActor.class);
		
		akkaApi.startActor(SendClientActor.class);
		akkaApi.startActor(SendActiveRangeActor.class);
		akkaApi.startActor(ActiveClientUpdateActor.class);

		// Entity
		akkaApi.startActor(EntityManagerActor.class);

		// Connection
		akkaApi.startActor(ConnectionManagerActor.class);

		// Maintenance actors.
		akkaApi.startActor(MapGeneratorMasterActor.class);
		akkaApi.startActor(MapGeneratorClientActor.class);

		// System actors.
		akkaApi.startActor(ZoneClusterListenerActor.class);
		akkaApi.startActor(HeartbeatActor.class);
		
		// Web/REST actors.
		akkaApi.startActor(CheckUsernameDataActor.class);
		akkaApi.startActor(ChangePasswordActor.class);
		akkaApi.startActor(RequestLoginActor.class);

		// Maybe this needs to be more sophisticated done only when we joined
		// the cluster.
		// Setup the init actor singelton for creation of the system.
		LOG.info("Starting the global init singeltons.");
		final ClusterSingletonManagerSettings settings = ClusterSingletonManagerSettings.create(system);
		final Props globalInitProps = SpringExtension.PROVIDER.get(system).props(InitGlobalActor.class);
		Props clusterProbs = ClusterSingletonManager.props(globalInitProps, PoisonPill.getInstance(), settings);
		system.actorOf(clusterProbs, "globalInit");

		// Trigger the startup script.
		scriptService.callScript("startup");

		LOG.info("Bestia Zone startup completed.");
	}
}
