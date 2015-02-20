package net.bestia.core.command;

import net.bestia.core.message.LoginMessage;
import net.bestia.core.message.Message;

/**
 * Requests a login to the server infrastructore. The
 * password is transmitted and checked if its valid. If this
 * is the case the account gets elevated and is then able to
 * use other commands then just the RequestLogin command.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
class LoginCommand extends Command {

	@Override
	public void execute(Message message, CommandContext ctx) {
		// Check password. If match elevate the connection.
		LoginMessage msg = (LoginMessage) message;
		
		// TODO Remove
		// Currently login regardless of the password, set it to the current password.
		/*
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
		
		ctx.getMessenger().sendMessage(msg);*/
	}


	@Override
	public String handlesMessageId() {
		return LoginMessage.MESSAGE_ID;
	}

}
