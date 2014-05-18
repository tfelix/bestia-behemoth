package net.bestia.core.command;

import java.util.concurrent.BlockingQueue;

import net.bestia.core.game.service.ServiceFactory;
import net.bestia.core.message.Message;
import net.bestia.core.message.PongMessage;

/**
 * Creates a Pong message which is echoed to the client. This is the answer of a
 * ping message.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
public class PingCommand extends Command {

	public PingCommand(Message message, ServiceFactory serviceFactory,
			BlockingQueue<Message> msgOutQueue) {
		super(message, serviceFactory, msgOutQueue);
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
		// Nothing is done here. Just a message will be returned.
		PongMessage msg = new PongMessage(sender.getAccountId());
		addMessage(msg);
	}

}
