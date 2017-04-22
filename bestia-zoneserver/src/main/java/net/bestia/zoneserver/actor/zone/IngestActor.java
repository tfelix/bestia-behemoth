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
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.battle.AttackPlayerUseActor;
import net.bestia.zoneserver.actor.bestia.ActivateBestiaActor;
import net.bestia.zoneserver.actor.bestia.BestiaInfoActor;
import net.bestia.zoneserver.actor.entity.EntityInteractionRequestActor;
import net.bestia.zoneserver.actor.entity.EntityMoveActor;
import net.bestia.zoneserver.actor.inventory.InventoryActor;
import net.bestia.zoneserver.actor.login.LoginActor;
import net.bestia.zoneserver.actor.login.LogoutActor;
import net.bestia.zoneserver.actor.map.MapRequestChunkActor;
import net.bestia.zoneserver.actor.map.TilesetRequestActor;

/**
 * This actor will once be the central routing actor which will resend all the
 * incoming messages to the correct destinations on the bestia system as soon as
 * the monolithic actor hierarchy is broken up into smaller parts.
 * 
 * It will also simplify the routing logic and helps to make the system easier
 * scalable via configuration files.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class IngestActor extends BestiaRoutingActor {

	public static final String NAME = "ingest";

	public IngestActor() {
		super(Arrays.asList(DoneMessage.class));

		// === Login ===
		// createActor(LoginActor.class);
		SpringExtension.actorOf(getContext(), LoginActor.class);

		// === Map ===
		SpringExtension.actorOf(getContext(), MapRequestChunkActor.class);
		SpringExtension.actorOf(getContext(), TilesetRequestActor.class);

		// === Inventory ===
		SpringExtension.actorOf(getContext(), InventoryActor.class);

		// === Bestias ===
		SpringExtension.actorOf(getContext(), BestiaInfoActor.class);
		SpringExtension.actorOf(getContext(), ActivateBestiaActor.class);

		// === Entities ===
		SpringExtension.actorOf(getContext(), EntityInteractionRequestActor.class);
		SpringExtension.actorOf(getContext(), EntityMoveActor.class);

		// === Attacking ===
		SpringExtension.actorOf(getContext(), AttackPlayerUseActor.class);

		// === Helper Actors
		SpringExtension.actorOf(getContext(), EngineReadyActor.class);

		// === House keeping actors ===
		SpringExtension.actorOf(getContext(), LogoutActor.class);
		SpringExtension.actorOf(getContext(), PingPongActor.class);
		SpringExtension.actorOf(getContext(), ZoneClusterListenerActor.class);

		// === DEVELOPMENT ===

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
