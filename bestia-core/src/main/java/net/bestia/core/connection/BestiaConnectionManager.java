package net.bestia.core.connection;

import net.bestia.core.message.Message;

/**
 * All services who whish to interface with the bestia gameserver should implement this
 * interface and route all communication to the server through this interface.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface BestiaConnectionManager {
	/**
	 * Sends the message to the given user connection. By setting the receiving account id to -1
	 * the message is send to ALL users (broadcast) beware that only certain types of messages
	 * can be send as broadcast. This should be checked by the implementation.
	 * @param msg
	 */
	public void sendMessage(Message message);
	
	/**
	 * The servers uses this method to elevate a connection to a higher state when an valid
	 * login has occurred.
	 * The uuid usually has to be provided from the outside since the real id of the account
	 * is not known until an elevation occurs. This uuid is temporarily used to identify the
	 * account.
	 * Note session handling itself is not a job of the server. This method is rather some kind
	 * of callback to the calling API that an account has gained higher privileges.
	 * 
	 * @param uuid Unique ID of the connection to elevate.
	 * @param Account
	 */
	public void elevateConnection(String uuid, int accountId);

	/**
	 * Connects the outside to the bestia behemoth game server. By generating messages and calling
	 * these method the message is set to execution on the gameserver infrastructure.
	 * 
	 * @param message Message to be executed.
	 */
	public void handleMessage(Message message);
}