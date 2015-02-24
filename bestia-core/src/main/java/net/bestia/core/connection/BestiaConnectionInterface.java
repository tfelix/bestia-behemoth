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
	 * The servers uses this method to elevate a connection to a higher state
	 * when an valid login has occurred. The uuid usually has to be provided
	 * from the outside since the real id of the account is not known until an
	 * elevation occurs. This uuid is temporarily used to identify the account.
	 * Note session handling itself is not a job of the server. This method is
	 * rather some kind of callback to the calling API that an account has
	 * gained higher privileges.
	 * 
	 * @param uuid
	 *            Unique ID of the connection to elevate.
	 * @param Account
	 */
	public void elevateConnection(String uuid, int accountId);
	
	/**
	 * Drops the connection with the given UUID. 
	 * 
	 * @param connectionId Connection to drop.
	 */
	public void dropConnection(UUID connectionId);
}