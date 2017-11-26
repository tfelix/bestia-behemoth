package net.bestia.zoneserver.actor.inventory;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import net.bestia.entity.component.InventoryService;
import net.bestia.messages.inventory.InventoryListMessage;
import net.bestia.messages.inventory.InventoryListRequestMessage;
import net.bestia.model.domain.PlayerItem;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.actor.zone.ClientMessageHandlerActor.RedirectMessage;

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
public class ListInventoryActor extends AbstractActor {

	public static final String NAME = "listInventory";

	private final InventoryService inventoryService;

	private final ActorRef sendClient;

	/**
	 * Ctor.
	 * 
	 * @param ctx
	 *            The {@link BestiaActorContext}.
	 */
	@Autowired
	public ListInventoryActor(InventoryService inventoryService, ActorRef msgHub) {

		this.inventoryService = Objects.requireNonNull(inventoryService);
		this.sendClient = SpringExtension.actorOf(getContext(), SendClientActor.class);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(InventoryListRequestMessage.class, this::onRequestInventory).build();
	}

	@Override
	public void preStart() throws Exception {
		final RedirectMessage msg = RedirectMessage.get(InventoryListRequestMessage.class);
		context().parent().tell(msg, getSelf());
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
