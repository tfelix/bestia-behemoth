package net.bestia.core.connection;

import java.io.IOException;
import java.util.UUID;

import net.bestia.core.message.Message;

/**
 * All services who whish to interface with the bestia gameserver should
 * implement this interface and route all communication to the server through
 * this interface.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface BestiaConnectionInterface {
	/**
	 * Sends the message to the given user connection. By setting the receiving
	 * account id to -1 the message is send to ALL users (broadcast) beware that
	 * only certain types of messages can be send as broadcast. This should be
	 * checked by the implementation.
	 * 
	 * @param msg
	 * @throws IOException
	 *             An IOException should be thrown if the message can not be
	 *             delivered.
	 */
	public void sendMessage(Message message) throws IOException;

	/**
	 * Should return true if we have a working (logged in) connection to a
	 * client with the given account id.
	 * 
	 * @param accountId
	 * @return TRUE if established connection exists. FALSE otherwise.
	 */
	public boolean isConnected(int accountId);

	
	/**
	 * Drops the connection with the given UUID. 
	 * 
	 * @param connectionId Connection to drop.
	 */
	public void dropConnection(UUID connectionId);
}