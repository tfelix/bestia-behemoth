package net.bestia.zoneserver.actor.inventory;

import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.UntypedActor;
import net.bestia.server.BestiaActorContext;


/**
 * This base actor will manage all inventory related messages for the bestia
 * inventory. The main purpose of this actor is to check the inventory message
 * and forward the message to the designated inventory actors.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InventoryActor extends UntypedActor {
	
	public InventoryActor(BestiaActorContext ctx) {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void onReceive(Object message) throws Exception {
		// TODO Auto-generated method stub

	}

	public static Props props(BestiaActorContext ctx) {
		return Props.create(InventoryActor.class, ctx).withDeploy(Deploy.local());
	}

}
