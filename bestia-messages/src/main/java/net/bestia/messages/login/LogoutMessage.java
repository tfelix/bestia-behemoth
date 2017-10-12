package net.bestia.messages.login;

import net.bestia.messages.JsonMessage;

/**
 * This message is send to the player in order to signal a (forced) logout from
 * the system. If the player disconnects by its own this message is not the
 * message to be send. In this case a {@link LogoutBroadcastMessage} will be
 * issued by the webserver so the zone can logout any pending entities
 * controlled by the player.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class LogoutMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "system.logout";


	public LogoutMessage(long accId) {
		super(accId);
		
		// no op.
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return "LogoutMessage[]";
	}

	@Override
	public LogoutMessage createNewInstance(long accountId) {
		return new LogoutMessage(accountId);
	}
}
