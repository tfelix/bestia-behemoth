package bestia.zoneserver.actor.inventory;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import bestia.zoneserver.bestia.InventoryService;
import bestia.messages.inventory.InventoryListMessage;
import bestia.messages.inventory.InventoryListRequestMessage;
import bestia.model.domain.PlayerItem;
import bestia.zoneserver.actor.SpringExtension;
import bestia.zoneserver.actor.zone.ClientMessageDigestActor;
import bestia.zoneserver.actor.zone.SendClientActor;

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
	 * 
	 * @param ctx
	 *            The {@link BestiaActorContext}.
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
