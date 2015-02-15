package net.bestia.core.command;

import java.util.concurrent.BlockingQueue;

import net.bestia.core.connection.BestiaConnectionInterface;
import net.bestia.core.game.model.Account;
import net.bestia.core.game.model.Password;
import net.bestia.core.game.service.AccountService;
import net.bestia.core.game.service.ServiceFactory;
import net.bestia.core.message.LoginMessage;
import net.bestia.core.message.Message;
import net.bestia.core.message.RequestLoginMessage;
import net.bestia.core.message.LoginMessage.LoginStatus;

/**
 * Requests a login to the server infrastructore. The
 * password is transmitted and checked if its valid. If this
 * is the case the account gets elevated and is then able to
 * use other commands then just the RequestLogin command.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
class RequestLoginCommand extends Command {
	
	private RequestLoginMessage message;
	private BestiaConnectionInterface connectionManager;

	public RequestLoginCommand(Message message, CommandContext context) {
		super(message, context);
		
		if(!(message instanceof RequestLoginMessage)) {
			throw new IllegalArgumentException("Message is not the correct type.");
		}
		
		this.message = (RequestLoginMessage)message;
	}

	@Override
	public PreExecutionCheck validateExecution() {
		return PreExecutionCheck.OK;
	}
	
	/**
	 * Account identification works differently in this command because the
	 * account id is yet unknown. We need to use the account identifier to find
	 * the correct account id.
	 */
	@Override
	protected void setupAccountService(Message message) {
		// Find the account and try to match the password.
		sender = context.getServiceFactory().getAccountServiceFactory()
				.getAccountByName(this.message.getAccountIdentifier());
	}

	@Override
	protected void executeCommand() {
		// Check password. If match elevate the connection.
		LoginMessage msg;
		
		// TODO Remove
		// Currently login regardless of the password, set it to the current password.
		sender.setPassword(message.getPasswordToken());
		
		if(sender.matchPassword(message.getPasswordToken())) {
			// Elevate the connection. Passwords matches.
			// TODO Create the login token.
			msg = new LoginMessage(sender.getAccountId(), LoginStatus.SUCCESS, "test123456");
			connectionManager.elevateConnection(message.getUuid(), sender.getAccountId());
		} else {
			msg = new LoginMessage(sender.getAccountId(), LoginStatus.ERROR, "");
			msg.setAccountId(message.getAccountId());
		}
		
		// Create login or logout messages.
		
		sendMessage(msg);
	}

}
