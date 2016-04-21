package net.bestia.zoneserver.msg2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

import net.bestia.messages.Message;
import net.bestia.zoneserver.messaging.MessageHandler;

/**
 * A message provider will distribute messages. Message handler can register
 * themselves with the provider and they will receive messages which will match
 * certain criteria.
 * <p>
 * This class is <b>threadsafe</b>.
 * </p>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MessageProvider implements MessageHandler {

	private class Tuple {
		public final Predicate<Message> predicate;
		public final MessageHandler handler;

		public Tuple(Predicate<Message> predicate, MessageHandler handler) {
			this.predicate = predicate;
			this.handler = handler;
		}
	}

	private final ReadWriteLock idLock = new ReentrantReadWriteLock();
	private final ReadWriteLock predicateLock = new ReentrantReadWriteLock();

	private final Map<String, List<MessageHandler>> messageIdHandler = new HashMap<>();

	private final List<Tuple> predicateHandler = new LinkedList<>();

	/**
	 * Subscribe for messages with a certain message id. This is the most simple
	 * form of message subscription.
	 * 
	 * @param messageId
	 * @param handler
	 */
	public void subscribe(String messageId, MessageHandler handler) {
		idLock.writeLock().lock();

		if (!messageIdHandler.containsKey(messageId)) {
			messageIdHandler.put(messageId, new ArrayList<>());
		}

		messageIdHandler.get(messageId).add(handler);

		idLock.writeLock().unlock();
	}

	/**
	 * Removes the given message handler from the subscription of the message
	 * id.
	 * 
	 * @param messageId
	 * @param handler
	 */
	public void unsubscribe(String messageId, MessageHandler handler) {
		idLock.writeLock().lock();

		if (messageIdHandler.containsKey(messageId)) {
			messageIdHandler.get(messageId).remove(handler);
		}

		idLock.writeLock().unlock();
	}

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
	public void subscribe(Predicate<Message> predicate, MessageHandler handler) {

		predicateLock.writeLock().lock();

		predicateHandler.add(new Tuple(predicate, handler));

		predicateLock.writeLock().unlock();

	}

	public void unsubscribe(Predicate<Message> predicate, MessageHandler handler) {

		predicateLock.writeLock().lock();

		predicateHandler.removeIf(x -> x.predicate == predicate && x.handler == handler);

		predicateLock.writeLock().unlock();

	}

	@Override
	public void handleMessage(Message msg) {
		// First check the ID locks.
		idLock.readLock().lock();

		for (MessageHandler handler : messageIdHandler.get(msg.getMessageId())) {
			handler.handleMessage(msg);
		}

		idLock.readLock().unlock();

		predicateLock.readLock().lock();

		for (Tuple t : predicateHandler) {
			if (t.predicate.test(msg)) {
				t.handler.handleMessage(msg);
			}
		}

		predicateLock.readLock().unlock();
	}
}
