package net.bestia.zoneserver.actor.inventory;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.bestia.zoneserver.actor.BestiaRoutingActor;


/**
 * This base actor will manage all inventory related messages for the bestia
 * inventory. The main purpose of this actor is to check the inventory message
 * and forward the message to the designated inventory actors.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class InventoryActor extends BestiaRoutingActor {
	
	public static final String NAME = "inventory";

	public InventoryActor() {
		
		// Create all the sub actors.
		createActor(DropItemActor.class, DropItemActor.NAME);
		createActor(ListInventoryActor.class, ListInventoryActor.NAME);
	}

	@Override
	protected void handleMessage(Object msg) {
		// no op.
	}

}
