package net.bestia.core.command;

import java.util.concurrent.BlockingQueue;

import net.bestia.core.game.service.ServiceFactory;
import net.bestia.core.message.LogoutMessage;
import net.bestia.core.message.Message;
import net.bestia.core.message.RequestLogoutMessage;

class RequestLogoutCommand extends Command {

	private RequestLogoutMessage message;

	public RequestLogoutCommand(Message message, CommandContext context) {
		super(message, context);
		
		if(!(message instanceof RequestLogoutMessage)) {
			throw new IllegalArgumentException("Message is not of correct type.");
		}
		
		this.message = (RequestLogoutMessage)message;
	}

	@Override
	public PreExecutionCheck validateExecution() {
		return PreExecutionCheck.OK;
	}

	@Override
	protected void executeCommand() {
		// TODO Logik einbauen.
		LogoutMessage msg = new LogoutMessage();
		sendMessage(msg);
	}

}
