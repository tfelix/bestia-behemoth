package net.bestia.zoneserver.actor.inventory;

import akka.actor.ActorRef;
import net.bestia.messages.inventory.InventoryListMessage;
import net.bestia.messages.inventory.InventoryListRequestMessage;
import net.bestia.model.domain.PlayerItem;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.ClientMessageDigestActor;
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.entity.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * This actor will create a list of the currently owned inventory items and send
 * them to the client. The inventory is dependend on the bestia which is
 * currently active.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class InventoryRequestActor extends ClientMessageDigestActor {

	public static final String NAME = "requestInventory";

	private final InventoryService inventoryService;

	private final ActorRef sendClient;

	/**
	 * Ctor.
	 */
	@Autowired
	public InventoryRequestActor(InventoryService inventoryService) {

		this.inventoryService = Objects.requireNonNull(inventoryService);
		this.sendClient = SpringExtension.actorOf(getContext(), SendClientActor.class);
		
		redirectConfig.match(InventoryListRequestMessage.class, this::onRequestInventory);
	}

	private void onRequestInventory(InventoryListRequestMessage msg) {
		final InventoryListRequestMessage ilmsg = (InventoryListRequestMessage) msg;

		// Generate a list of inventory items.
		final InventoryListMessage invMsg = new InventoryListMessage(ilmsg.getAccountId());
		final List<PlayerItem> items = inventoryService.findPlayerItemsForAccount(ilmsg.getAccountId());
		invMsg.setPlayerItems(items);

		sendClient.tell(invMsg, getSelf());
	}
}
