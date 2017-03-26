package net.bestia.zoneserver.actor.zone;

import java.util.Arrays;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
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

		// === Login ===
		//createActor(LoginActor.class);
		SpringExtension.actorOf(getContext(), LoginActor.class);

		// === Map ===
		SpringExtension.actorOf(getContext(), MapRequestChunkActor.class);
		SpringExtension.actorOf(getContext(), TilesetRequestActor.class);

		// === Inventory ===
		SpringExtension.actorOf(getContext(), InventoryActor.class);

		// === Bestias ===
		SpringExtension.actorOf(getContext(), BestiaInfoActor.class);
		SpringExtension.actorOf(getContext(), ActivateBestiaActor.class);
		SpringExtension.actorOf(getContext(), EntitySpawnActor.class);

		// === Entities ===
		SpringExtension.actorOf(getContext(), EntityInteractionRequestActor.class);
		SpringExtension.actorOf(getContext(), EntityMoveActor.class);

		// === Chat ===
		SpringExtension.actorOf(getContext(), ChatActor.class);

		// === Attacking ===
		SpringExtension.actorOf(getContext(), AttackPlayerUseActor.class);

		// === Helper Actors
		SpringExtension.actorOf(getContext(), EngineReadyActor.class);

		// === House keeping actors ===
		SpringExtension.actorOf(getContext(), LogoutActor.class);
		SpringExtension.actorOf(getContext(), PingPongActor.class);
		SpringExtension.actorOf(getContext(), ZoneClusterListenerActor.class);

		// === DEVELOPMENT ===
		SpringExtension.actorOf(getContext(), SpawnActorHelper.class);

		// Setup the init actor singelton for creation of the system.
		final ClusterSingletonManagerSettings settings = ClusterSingletonManagerSettings.create(getContext().system());
		final Props globalInitProps = SpringExtension.getSpringProps(getContext(), InitGlobalActor.class);
		Props clusterProbs = ClusterSingletonManager.props(globalInitProps, PoisonPill.getInstance(), settings);
		getContext().actorOf(clusterProbs, "globalInit");

		// Try to do the global init if it has not been done before.
		final ClusterSingletonProxySettings proxySettings = ClusterSingletonProxySettings.create(getContext().system());
		clusterProbs = ClusterSingletonProxy.props("/user/globalInit", proxySettings);
		final ActorRef initProxy = getContext().actorOf(clusterProbs, "globalInitProxy");
		initProxy.tell(new StartInitMessage(), getSelf());
	}

	@Override
	protected void handleMessage(Object msg) {
		// no op.
	}
}
