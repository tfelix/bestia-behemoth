package net.bestia.zoneserver.actor.inventory;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;

@Component
@Scope("prototype")
public class DropItemActor extends AbstractActor {

	public static final String NAME = "dropItem";
	
	public DropItemActor() {
	}

	@Override
	public Receive createReceive() {
		// TODO Auto-generated method stub
		return null;
	}

}
