package net.bestia.zoneserver.actor.inventory;

import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.UntypedActor;
import net.bestia.messages.Message;
import net.bestia.messages.inventory.InventoryListRequestMessage;
import net.bestia.model.service.InventoryService;
import net.bestia.server.BestiaActorContext;
import net.bestia.zoneserver.inventory.Inventory;
import net.bestia.zoneserver.zone.entity.PlayerBestiaEntity;

/**
 * This actor will create a list of the currently owned inventory items and send
 * them to the client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InventoryListActor extends UntypedActor {

	final InventoryService inventoryService;

	public InventoryListActor(BestiaActorContext ctx) {

		inventoryService = ctx.getSpringContext().getBean(InventoryService.class);
	}

	public static Props props(BestiaActorContext ctx) {
		return Props.create(InventoryListActor.class, ctx).withDeploy(Deploy.local());
	}

	@Override
	public void onReceive(Object message) throws Exception {

		if (!(message instanceof InventoryListRequestMessage)) {
			unhandled(message);
			return;
		}

		final PlayerBestiaEntity pb = new PlayerBestiaEntity();

		final Inventory invManager = new Inventory(pb, inventoryService);

		final Message invListMessage = invManager.getInventoryListMessage();

		getSender().tell(invListMessage, getSelf());
	}

}
