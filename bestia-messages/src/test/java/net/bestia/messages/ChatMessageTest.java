package net.bestia.messages;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.messages.chat.ChatMessage;

public class ChatMessageTest {
	
	@Test
	public void message_path_test() {
		ChatMessage msg = new ChatMessage();
		Assert.assertEquals("zone/account/0", msg.getMessagePath());
		
		// after setting the account id path must be up to date.
		msg.setAccountId(1337L);
		Assert.assertEquals("zone/account/1337", msg.getMessagePath());
		
		// The zone now sets different values like sender nickname.
		msg.setSenderNickname("max");
		
		// We get "returned" messages to the client.
		ChatMessage msg2 = ChatMessage.getEchoMessage(2L, msg);
		Assert.assertEquals("account/2", msg2.getMessagePath());
		
		// Must be up to date.
		msg2.setAccountId(1L);
		Assert.assertEquals("account/1", msg2.getMessagePath());
	}
}
