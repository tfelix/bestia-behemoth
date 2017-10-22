package net.bestia.zoneserver;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.client.ClusterClientReceptionist;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import net.bestia.server.EntryActorNames;
import net.bestia.zoneserver.actor.BestiaRootActor;
import net.bestia.zoneserver.actor.EntityShardMessageExtractor;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.entity.EntityActor;
import net.bestia.zoneserver.actor.zone.ClusterControlActor;
import net.bestia.zoneserver.actor.zone.IngestActor;
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

	private final ActorSystem system;
	private final ScriptService scriptService;

	@Autowired
	public ZoneStarter(ActorSystem system, ScriptService scriptService) {

		this.system = Objects.requireNonNull(system);
		this.scriptService = Objects.requireNonNull(scriptService);
	}

	@Override
	public void run(String... strings) throws Exception {
		LOG.info("Starting actor system...");

		registerShardedActors();
		
		registerSingeltons();
		
		final ActorRef rootActor = SpringExtension.actorOf(system, BestiaRootActor.class);		

		// Trigger the startup script.
		scriptService.callScript("startup");

		LOG.info("Bestia Zone startup completed.");
	}

	private void registerSingeltons() {
		// Maybe this needs to be more sophisticated done only when we joined
		// the cluster.
		// Setup the init actor singelton for creation of the system.
		LOG.info("Starting the global init singeltons.");
		final ClusterSingletonManagerSettings settings = ClusterSingletonManagerSettings.create(system);
		final Props globalInitProps = SpringExtension.getSpringProps(system, ClusterControlActor.class);
		Props clusterProbs = ClusterSingletonManager.props(globalInitProps, PoisonPill.getInstance(), settings);
		system.actorOf(clusterProbs, "globalInit");
	}

	private void registerShardedActors() {
		LOG.info("Register the sharded actor.");
		final ClusterShardingSettings settings = ClusterShardingSettings.create(system);
		final ClusterSharding sharding = ClusterSharding.get(system);
		final Props props = SpringExtension.getSpringProps(system, EntityActor.class);
		final EntityShardMessageExtractor extractor = new EntityShardMessageExtractor();
		sharding.start(EntryActorNames.SHARD_ENTITY, props, settings, extractor);
	}
}
