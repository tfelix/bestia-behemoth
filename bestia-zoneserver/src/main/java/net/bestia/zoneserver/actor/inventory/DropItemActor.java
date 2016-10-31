package net.bestia.zoneserver.actor.inventory;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.bestia.zoneserver.actor.BestiaRoutingActor;

@Component
@Scope("prototype")
public class DropItemActor extends BestiaRoutingActor {

	public static final String NAME = "dropItem";

	@Override
	protected void handleMessage(Object msg) {
		// TODO Auto-generated method stub
		
	}

}
