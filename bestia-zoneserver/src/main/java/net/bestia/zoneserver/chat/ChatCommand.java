package net.bestia.zoneserver.chat;

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
	 * @param accId
	 *            Account id which issued this command.
	 * @param text
	 *            Chat text
	 */
	void executeCommand(long accId, String text);
}
