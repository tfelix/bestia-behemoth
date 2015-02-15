package net.bestia.core.command;

import net.bestia.core.message.Message;
import net.bestia.core.message.PongMessage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Creates a Pong message which is echoed to the client. This is the answer of a
 * ping message.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
class PingCommand extends Command {
	
	private final static Logger log = LogManager.getLogger(PingCommand.class);

	public PingCommand(Message message, CommandContext context) {
		super(message, context);
	}

	/**
	 * This command is always valid do return zero.
	 */
	@Override
	public PreExecutionCheck validateExecution() {
		// Always valid.
		return PreExecutionCheck.OK;
	}

	@Override
	protected void executeCommand() {
		log.trace("Executing PingCommand.");
		
		// Nothing is done here. Just a message will be returned.
		PongMessage msg = new PongMessage(sender.getAccountId());
		sendMessage(msg);
	}
	
	@Override
	public String toString() {
		return "PingCommand[]";
	}

}
