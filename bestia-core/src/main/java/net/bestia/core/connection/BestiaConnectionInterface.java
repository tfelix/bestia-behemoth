package net.bestia.core.connection;

import java.io.IOException;
import java.util.UUID;

import net.bestia.messages.Message;

/**
 * All services who whish to interface with the bestia gameserver should implement this interface and route all
 * communication to the server through this interface.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface BestiaConnectionInterface {
	/**
	 * Sends the message to the given user connection. By setting the receiving account id to -1 the message is send to
	 * ALL users (broadcast) beware that only certain types of messages can be send as broadcast. This should be checked
	 * by the implementation.
	 * <p>On the implementation side it might be necessary to listen to some types of messages. In
	 * order to disconnect a player the server would send a logout message. This has to be examined by the
	 * implementation and the offending connection must be closed.</p>
	 * 
	 * @param msg
	 * @throws IOException
	 *             An IOException should be thrown if the message can not be delivered.
	 */
	public void sendMessage(Message message) throws IOException;

	/**
	 * Should return true if we have a working (logged in) connection to a client with the given account id.
	 * 
	 * @param accountId
	 * @return TRUE if established connection exists. FALSE otherwise.
	 */
	public boolean isConnected(int accountId);

	/**
	 * Returns the number of currently connected players.
	 * 
	 * @return Number of currently connected players.
	 */
	public int getConnectedPlayers();
}