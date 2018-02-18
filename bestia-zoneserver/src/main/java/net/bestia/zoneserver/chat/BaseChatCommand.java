package net.bestia.zoneserver.chat;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.messages.MessageApi;
import net.bestia.messages.chat.ChatMessage;

abstract class BaseChatCommand implements ChatCommand {

	private final MessageApi akkaApi;

	@Autowired
	public BaseChatCommand(MessageApi akkaApi) {

		this.akkaApi = Objects.requireNonNull(akkaApi);
	}

	/**
	 * Defines a help text in case the arguments of the command are not ready.
	 * 
	 * @return A short helping text for the command.
	 */
	abstract protected String getHelpText();

	/**
	 * This text is send right to the user who invoked the command.
	 * 
	 * @param text
	 *            The text to send to the user.
	 * @param accId
	 *            The account id to send the text to.
	 */
	protected void sendSystemMessage(long accId, String text) {
		final ChatMessage replyMsg = ChatMessage.getSystemMessage(accId, text);
		akkaApi.sendToClient(accId, replyMsg);
	}
}
