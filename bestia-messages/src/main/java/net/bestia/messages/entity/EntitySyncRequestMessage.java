package net.bestia.messages.entity;

import net.bestia.messages.JsonMessage;

/**
 * Requests the server to send a full list with all visible entities to the
 * client. This message is issued by the engine if a reload has occured or the
 * engine is unsure to have synced to all entities.
 * 
 * @author Thomas Felix
 *
 */
public class EntitySyncRequestMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "entity.sync";


	public EntitySyncRequestMessage(long accId) {
		super(accId);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public JsonMessage createNewInstance(long accountId) {
		return new EntitySyncRequestMessage(accountId);
	}

}
