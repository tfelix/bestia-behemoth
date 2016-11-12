package net.bestia.zoneserver.actor.inventory;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.bestia.messages.inventory.InventoryListMessage;
import net.bestia.messages.inventory.InventoryListRequestMessage;
import net.bestia.model.service.InventoryService;
import net.bestia.model.service.PlayerBestiaService;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.inventory.Inventory;
import net.bestia.zoneserver.service.PlayerEntityService;

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
	private final PlayerEntityService entityService;

	/**
	 * Ctor.
	 * 
	 * @param ctx
	 *            The {@link BestiaActorContext}.
	 */
	@Autowired
	public ListInventoryActor(InventoryService inventoryService,
			PlayerEntityService entityService,
			PlayerBestiaService bestiaService) {
		super(Arrays.asList(InventoryListRequestMessage.class));

		this.inventoryService = Objects.requireNonNull(inventoryService);
		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	protected void handleMessage(Object msg) {
		
		final InventoryListRequestMessage ilmsg = (InventoryListRequestMessage) msg;
		
		final PlayerBestiaEntity pbe = entityService.getActivePlayerEntity(ilmsg.getAccountId());
		
		final Inventory invManager = new Inventory(pbe, inventoryService);
		final InventoryListMessage invListMessage = invManager.getInventoryListMessage();

		sendClient(invListMessage);
	}

}
