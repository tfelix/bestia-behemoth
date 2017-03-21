package net.bestia.zoneserver.actor.zone;

import java.util.Arrays;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.cluster.singleton.ClusterSingletonProxy;
import akka.cluster.singleton.ClusterSingletonProxySettings;
import net.bestia.messages.internal.DoneMessage;
import net.bestia.messages.internal.StartInitMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.actor.SpawnActorHelper;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.battle.AttackPlayerUseActor;
import net.bestia.zoneserver.actor.bestia.ActivateBestiaActor;
import net.bestia.zoneserver.actor.bestia.BestiaInfoActor;
import net.bestia.zoneserver.actor.chat.ChatActor;
import net.bestia.zoneserver.actor.entity.EntityInteractionRequestActor;
import net.bestia.zoneserver.actor.entity.EntityMoveActor;
import net.bestia.zoneserver.actor.entity.EntitySpawnActor;
import net.bestia.zoneserver.actor.inventory.InventoryActor;
import net.bestia.zoneserver.actor.map.MapRequestChunkActor;
import net.bestia.zoneserver.actor.map.TilesetRequestActor;

/**
 * Central actor for handling zone related messages. This actor will redirect
 * the various massages to the different actors and build up the initial needed
 * actor tree.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component("ZoneActor")
@Scope("prototype")
public class ZoneActor extends BestiaRoutingActor {

	public static final String NAME = "zone";

	public ZoneActor() {
		super(Arrays.asList(DoneMessage.class));

		final ActorSystem system = getContext().system();

		// === Login ===
		SpringExtension.actorOf(system, LoginActor.class);

		// === Map ===
		SpringExtension.actorOf(system, MapRequestChunkActor.class);
		SpringExtension.actorOf(system, TilesetRequestActor.class);

		// === Inventory ===
		SpringExtension.actorOf(system, InventoryActor.class);

		// === Bestias ===
		SpringExtension.actorOf(system, BestiaInfoActor.class);
		SpringExtension.actorOf(system, ActivateBestiaActor.class);
		SpringExtension.actorOf(system, EntitySpawnActor.class);

		// === Entities ===
		SpringExtension.actorOf(system, EntityInteractionRequestActor.class);
		SpringExtension.actorOf(system, EntityMoveActor.class);

		// === Chat ===
		SpringExtension.actorOf(system, ChatActor.class);

		// === Attacking ===
		SpringExtension.actorOf(system, AttackPlayerUseActor.class);

		// === Helper Actors
		SpringExtension.actorOf(system, EngineReadyActor.class);

		// === House keeping actors ===
		SpringExtension.actorOf(system, LogoutActor.class);
		SpringExtension.actorOf(system, PingPongActor.class);
		SpringExtension.actorOf(system, ZoneClusterListenerActor.class);

		// === DEVELOPMENT ===
		SpringExtension.actorOf(system, SpawnActorHelper.class);

		// Setup the init actor singelton for creation of the system.
		final ClusterSingletonManagerSettings settings = ClusterSingletonManagerSettings.create(system);
		final Props globalInitProps = SpringExtension.getSpringProps(system, InitGlobalActor.class);
		Props clusterProbs = ClusterSingletonManager.props(globalInitProps, PoisonPill.getInstance(), settings);
		system.actorOf(clusterProbs, "globalInit");

		// Try to do the global init if it has not been done before.
		final ClusterSingletonProxySettings proxySettings = ClusterSingletonProxySettings.create(system);
		clusterProbs = ClusterSingletonProxy.props("/user/globalInit", proxySettings);
		final ActorRef initProxy = system.actorOf(clusterProbs, "globalInitProxy");
		initProxy.tell(new StartInitMessage(), getSelf());
	}

	@Override
	protected void handleMessage(Object msg) {
		// TODO Auto-generated method stub

	}
}
