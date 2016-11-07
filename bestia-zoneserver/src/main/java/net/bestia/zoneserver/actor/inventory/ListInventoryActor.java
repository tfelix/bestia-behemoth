package net.bestia.zoneserver.actor.inventory;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import net.bestia.messages.inventory.InventoryListRequestMessage;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.actor.zone.SendClientActor;

/**
 * This actor will create a list of the currently owned inventory items and send
 * them to the client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class ListInventoryActor extends BestiaRoutingActor {

	public static final String NAME = "listInventory";
	
	private final InventoryService inventoryService;
	private final ActorRef responder;
	

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
		this.responder = createActor(SendClientActor.class, SendClientActor.NAME);
	}

	@Override
	protected void handleMessage(Object msg) {
		// TODO Das hier implementieren.
/*
		final PlayerBestiaEntity pb = new PlayerBestiaEntity();
		final Inventory invManager = new Inventory(pb, inventoryService);
		final Message invListMessage = invManager.getInventoryListMessage();

		responder.tell(invListMessage, getSelf());*/
	}

}
