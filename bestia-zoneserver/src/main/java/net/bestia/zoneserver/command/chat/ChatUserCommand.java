package net.bestia.zoneserver.command.chat;

import net.bestia.messages.ChatMessage;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.proxy.PlayerEntityProxy;

/**
 * Implements the various chat commands. In order to add a new command just
 * implement this interface inside this package. The command will be
 * automatically picked up and be used.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
interface ChatUserCommand {

	/**
	 * Will be executed when the chat token of this command is found. Must
	 * implement the logic which is used for this command.
	 * 
	 * @param message
	 *            The chat message.
	 * @param player 
	 * @param ctx
	 *            The {@link CommandContext}.
	 * @return TRUE if the command
	 */
	void execute(ChatMessage message, PlayerEntityProxy player, CommandContext ctx);

	/**
	 * The token of the command for which this implementation is responsible.
	 * E.g. /item.
	 * 
	 * @return The token of the command to be executed with this implementation.
	 */
	String getChatToken();

	/**
	 * Implement this method to set the at least needed userlevel in order to
	 * perform this command on the server.
	 * 
	 * @return The minimum needed {@link UserLevel} in order to execute this
	 *         command.
	 */
	UserLevel getNeededUserLevel();

}