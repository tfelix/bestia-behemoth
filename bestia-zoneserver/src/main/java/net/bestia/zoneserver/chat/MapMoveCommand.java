package net.bestia.zoneserver.chat;

/**
 * Moves the player to the given map coordinates if he has gm permissions.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapMoveCommand implements ChatCommand {

	@Override
	public boolean isCommand(String text) {
		return text.startsWith("/mm");
	}

	@Override
	public void executeCommand(long accId, String text) {
		// TODO Auto-generated method stub
		
	}

}
