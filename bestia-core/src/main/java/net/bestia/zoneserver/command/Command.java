package net.bestia.zoneserver.command;

import net.bestia.messages.Message;

/**
 * This class represents the very basic interface for commands which are
 * executed by the bestia gameserver. Commands are created out of messages from
 * the clients.
 * When sub-classing DO NOT introduce state variables, at least none which are 
 * written since these commands are shared between threads and exist solely to 
 * execute the messages from the client. All objects in the command context 
 * must be thread safe.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
public abstract class Command {


	/**
	 * Ctor. Creates a basic command object.
	 * 
	 * @param account
	 * @param Message
	 *            Original message holding the client request.
	 * 
	 */
	public Command() {

	}

	/**
	 * Returns the ID of the message which is handles by this command. The
	 * factory will use this information to auto-register the commands and
	 * associate them with a message id.
	 * 
	 * @return
	 */
	public abstract String handlesMessageId();

	/**
	 * Execute the command logic.
	 * 
	 * @param message
	 * @param ctx
	 */
	public abstract void execute(Message message, CommandContext ctx);
}
