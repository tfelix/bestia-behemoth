package net.bestia.zoneserver.actor.entity;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.bestia.zoneserver.actor.BestiaRoutingActor;

/**
 * This actor looks up all the other entities in visual range and sends an
 * update to the player if a new entity is no longer seen anymore or becomes
 * visible inside the sight range.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class EntitySightUpdateActor extends BestiaRoutingActor {
	
	

	@Override
	protected void handleMessage(Object msg) {
		// TODO Auto-generated method stub
		
	}

}
