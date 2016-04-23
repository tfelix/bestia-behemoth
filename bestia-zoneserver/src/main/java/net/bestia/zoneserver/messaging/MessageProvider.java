package net.bestia.zoneserver.messaging;

import java.util.function.Predicate;

import net.bestia.messages.Message;

/**
 * Message provider are used to distribute messages internally inside the
 * servers. One can subscribe to via criterions (the easiest is via an message
 * ID) but since the ID is not enought to filter through bestias complay message
 * system one can use the predicate via an lambda to filter for complex message
 * criterions.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
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
	 *            The message ID to unsubscribe from.
	 * @param handler
	 *            The handler which was used for subscription.
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

	/**
	 * Removes the given message handler from the subscription of the message
	 * via the predicate.
	 * 
	 * @param predicate
	 *            The predicate which was used to subscribe for.
	 * @param handler
	 *            The handler which was used to subscribe to the messages.
	 */
	void unsubscribe(Predicate<Message> predicate, MessageHandler handler);
}