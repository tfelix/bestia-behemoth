package net.bestia.zoneserver.messaging.routing;

import java.util.HashSet;
import java.util.Set;

import net.bestia.messages.ChatMessage;
import net.bestia.messages.Message;

public class ChatMessageFilter implements MessageFilter {

	private final Set<ChatMessage.Mode> allowedModes = new HashSet<>();
	
	public void addChatMode() {
		
	}
	
	@Override
	public boolean handlesMessage(Message msg) {
		
		if(!(msg instanceof ChatMessage)) {
			return false;
		}
		
		final ChatMessage chatMsg = (ChatMessage) msg;
		
		allowedModes.contains(chatMsg.getChatMode());
		
		
		return false;
	}

}
