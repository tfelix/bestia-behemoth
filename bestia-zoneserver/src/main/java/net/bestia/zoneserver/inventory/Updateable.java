package net.bestia.zoneserver.inventory;

import java.util.List;

import net.bestia.messages.Message;

/**
 * Saves a list with update messages. Should be used to inform the client about
 * changes after some internal operations.
 * 
 * @author Thomas
 *
 */
public interface Updateable {

	List<Message> getUpdates();

}
