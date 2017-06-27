package net.bestia.zoneserver.actor.zone;

import java.util.Arrays;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.bestia.messages.internal.DoneMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.battle.AttackPlayerUseActor;
import net.bestia.zoneserver.actor.bestia.ActivateBestiaActor;
import net.bestia.zoneserver.actor.bestia.BestiaInfoActor;
import net.bestia.zoneserver.actor.chat.ChatActor;
import net.bestia.zoneserver.actor.entity.EntityInteractionRequestActor;
import net.bestia.zoneserver.actor.entity.EntityMovementActor;
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
		SpringExtension.actorOf(getContext(), LoginActor.class);
		
		// === Chat ===
		SpringExtension.actorOf(getContext(), ChatActor.class);

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
		SpringExtension.actorOf(getContext(), EntityMovementActor.class);

		// === Attacking ===
		SpringExtension.actorOf(getContext(), AttackPlayerUseActor.class);

		// === House keeping actors ===
		SpringExtension.actorOf(getContext(), LogoutActor.class);
		SpringExtension.actorOf(getContext(), PingPongActor.class);

		// === DEVELOPMENT ===

	}

	@Override
	protected void handleMessage(Object msg) {
		// no op.
	}

}
