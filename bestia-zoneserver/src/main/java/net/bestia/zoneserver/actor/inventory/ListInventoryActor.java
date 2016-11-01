package net.bestia.zoneserver.actor.inventory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.UntypedActor;
import net.bestia.messages.Message;
import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.inventory.InventoryListRequestMessage;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.inventory.Inventory;

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
	private final Set<Class<? extends Message>> HANDLED_CLASSES = Collections.unmodifiableSet(new HashSet<>(
			Arrays.asList(InventoryListRequestMessage.class)));
	
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

		this.inventoryService = Objects.requireNonNull(inventoryService);
		this.responder = createActor(SendClientActor.class, SendClientActor.NAME);
	}
	
	@Override
	protected Set<Class<? extends Message>> getHandledMessages() {
		return HANDLED_CLASSES;
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
