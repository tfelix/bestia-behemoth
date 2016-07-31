package net.bestia.zoneserver.actor.entity;

import akka.actor.UntypedActor;

/**
 * When the entity will receive a walk request message with a path we will spawn
 * up a TimedMoveActor which will periodically wake up and move a given entity
 * to the desired location.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class TimedMoveActor extends UntypedActor {

	@Override
	public void onReceive(Object arg0) throws Exception {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void postStop() throws Exception {
		super.postStop();
		
		instance.getLifecycleService().shutdown();
	}

}
