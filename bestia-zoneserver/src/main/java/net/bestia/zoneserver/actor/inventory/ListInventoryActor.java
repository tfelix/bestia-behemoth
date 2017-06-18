package net.bestia.zoneserver.actor.inventory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.bestia.messages.inventory.InventoryListMessage;
import net.bestia.messages.inventory.InventoryListRequestMessage;
import net.bestia.model.domain.PlayerItem;
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.service.InventoryService;

/**
 * This actor will create a list of the currently owned inventory items and send
 * them to the client. The inventory is dependend on the bestia which is
 * currently active.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class ListInventoryActor extends BestiaRoutingActor {

	public static final String NAME = "listInventory";

	private final InventoryService inventoryService;

	/**
	 * Ctor.
	 * 
	 * @param ctx
	 *            The {@link BestiaActorContext}.
	 */
	@Autowired
	public ListInventoryActor(InventoryService inventoryService) {
		super(Arrays.asList(InventoryListRequestMessage.class));

		this.inventoryService = Objects.requireNonNull(inventoryService);
	}

	@Override
	protected void handleMessage(Object msg) {
		
		final InventoryListRequestMessage ilmsg = (InventoryListRequestMessage) msg;
		
		// Generate a list of inventory items.
		final InventoryListMessage invMsg = new InventoryListMessage(ilmsg.getAccountId());
		final List<PlayerItem> items = inventoryService.findPlayerItemsForAccount(ilmsg.getAccountId());
		invMsg.setPlayerItems(items);

		AkkaSender.sendClient(getContext(), invMsg);
	}

}
