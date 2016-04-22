package net.bestia.zoneserver.messaging;

import java.util.function.Predicate;

import net.bestia.messages.Message;

public interface MessageProvider extends MessageHandler {

	/**
	 * Subscribe for messages with a certain message id. This is the most simple
	 * form of message subscription.
	 * 
	 * @param messageId
	 * @param handler
	 */
	void subscribe(String messageId, MessageHandler handler);

	/**
	 * Removes the given message handler from the subscription of the message
	 * id.
	 * 
	 * @param messageId
	 * @param handler
	 */
	void unsubscribe(String messageId, MessageHandler handler);

	/**
	 * Subscribes to messages whose predicate will return true. This is a far
	 * more powerful approach an might be needed in order to to complex message
	 * routing and subscriptions.
	 * 
	 * @param predicate
	 *            Checks the incoming message for a custom criterion and must
	 *            return true if the message handler should receive this
	 *            message.
	 * @param handler
	 *            The handler subscribing to the messages.
	 */
	void subscribe(Predicate<Message> predicate, MessageHandler handler);

	void unsubscribe(Predicate<Message> predicate, MessageHandler handler);
}