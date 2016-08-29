package net.bestia.zoneserver.actor.inventory;

import akka.actor.UntypedActor;


/**
 * This base actor will manage all inventory related messages for the bestia
 * inventory. The main purpose of this actor is to check the inventory message
 * and forward the message to the designated inventory actors.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InventoryActor extends UntypedActor {
	
	public InventoryActor() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void onReceive(Object message) throws Exception {
		// TODO Auto-generated method stub

	}

}
