package net.bestia.zoneserver.actor.zone;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.battle.AttackPlayerUseActor;
import net.bestia.zoneserver.actor.connection.ConnectionStatusActor;
import net.bestia.zoneserver.actor.entity.EntityInteractionRequestActor;
import net.bestia.zoneserver.actor.entity.EntitySyncActor;
import net.bestia.zoneserver.actor.inventory.InventoryActor;
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

		// === Map ===
		SpringExtension.actorOf(getContext(), MapRequestChunkActor.class);
		SpringExtension.actorOf(getContext(), TilesetRequestActor.class);

		// === Inventory ===
		SpringExtension.actorOf(getContext(), InventoryActor.class);


		// === Entities ===
		SpringExtension.actorOf(getContext(), EntityInteractionRequestActor.class);
		SpringExtension.actorOf(getContext(), EntitySyncActor.class);

		// === Attacking ===
		SpringExtension.actorOf(getContext(), AttackPlayerUseActor.class);

		// === House keeping actors ===
		

	}

	@Override
	protected void handleMessage(Object msg) {
		// no op.
	}

}
