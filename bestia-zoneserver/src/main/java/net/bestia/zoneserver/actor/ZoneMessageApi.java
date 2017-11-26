package net.bestia.zoneserver.actor;

import akka.actor.ActorRef;
import net.bestia.messages.MessageApi;

public interface ZoneMessageApi extends MessageApi {
	
	public void setReceivingActor(ActorRef singleClient, ActorRef allClientsRange, ActorRef entity);

}
