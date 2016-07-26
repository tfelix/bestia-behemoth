package net.bestia.zoneserver.command;

import net.bestia.messages.Message;

/**
 * This class represents the very basic interface for commands which are executed by the bestia gameserver. Commands are
 * created out of messages from the clients. When sub-classing DO NOT introduce state variables, at least none which are
 * written since these commands are shared between threads and exist solely to execute the messages from the client. All
 * objects in the command context must be thread safe.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
public abstract class Command implements Runnable {

	private CommandContext ctx;
	private Message message;

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

	public void setCommandContext(CommandContext ctx) {
		this.ctx = ctx;
	}

	public void setMessage(Message msg) {
		this.message = msg;
	}

	/**
	 * Returns the ID of the message which is handles by this command. The factory will use this information to
	 * auto-register the commands and associate them with a message id.
	 * 
	 * @return
	 */
	public abstract String handlesMessageId();

	@Override
	public void run() {

		if (message == null || ctx == null) {
			throw new IllegalStateException("Message and/or CommandContext was not set before execution.");
		}

		initialize();
		execute(message, ctx);
	}

	/**
	 * Can be overwritten to do initialization work before the main execute method is called.
	 */
	protected void initialize() {
		// no op.
	}

	/**
	 * Execute the command logic.
	 * 
	 * @param message
	 * @param ctx
	 */
	protected abstract void execute(Message message, CommandContext ctx);
}
