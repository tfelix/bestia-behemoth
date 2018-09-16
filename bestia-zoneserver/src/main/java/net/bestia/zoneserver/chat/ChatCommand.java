package net.bestia.zoneserver.chat;

import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.Companion.UserLevel;

public interface ChatCommand {

	/**
	 * Checks if this is a correct chat command and can be handled by this
	 * command.
	 * 
	 * @param text
	 *            Chat text.
	 * @return TRUE if the command can be handled by this {@link ChatCommand}
	 *         implementation.
	 */
	boolean isCommand(String text);

	/**
	 * Executes the chat command which was issued by the given account id.
	 * 
	 * @param account Account which issued this command.
	 * @param text
	 *            Chat text typed by the user.
	 */
	void executeCommand(Account account, String text);

	/**
	 * Gives the minimum user level required to execute this command.
	 * 
	 * @return The minimum userlevel required to use this command.
	 */
	UserLevel requiredUserLevel();
}
