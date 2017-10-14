package net.bestia.entity;

import org.springframework.stereotype.Component;

import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.messages.MessageApi;

/**
 * Drops all messages.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class NullMessageApi implements MessageApi {

	@Override
	public void sendToClient(JsonMessage message) {
		// no op.
	}

	@Override
	public void sendToActiveClientsInRange(EntityJsonMessage message) {
		// no op.
	}

	@Override
	public void sendToEntity(long entityId, Object msg) {
		// no op.
	}

}
