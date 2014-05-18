package net.bestia.core.command;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import net.bestia.core.game.service.ServiceFactory;
import net.bestia.core.message.ChatMessage;
import net.bestia.core.message.Message;

import org.junit.Test;

public class ChatCommandTest {

	@Test
	public void executeCommand_test() {
		BlockingQueue<Message> queue = new LinkedBlockingQueue<Message>();
		// Muss gemockt werden.
		ServiceFactory serviceFactory = null;
		
		ChatMessage message = new ChatMessage();
		message.setAccountId(1);
		message.setChatMode(ChatMessage.Mode.GUILD);
		message.setText("Hello World.");
		
		ChatCommand cmd = new ChatCommand(message, serviceFactory, queue);
	}
}
