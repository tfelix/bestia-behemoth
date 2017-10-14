package net.bestia.entity;

import org.springframework.stereotype.Component;

import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.messages.MessageApi;

@Component
public class NullMessageApi implements MessageApi {

	@Override
	public void sendToClient(JsonMessage message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendActiveInRangeClients(EntityJsonMessage message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendToActor(String actorName, Object message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendEntityActor(long entityId, Object msg) {
		// TODO Auto-generated method stub
		
	}

}
