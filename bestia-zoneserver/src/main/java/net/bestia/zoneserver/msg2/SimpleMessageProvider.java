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
public class SimpleMessageProvider implements MessageProvider {

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

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.msg2.IMessageProvider#subscribe(java.lang.String, net.bestia.zoneserver.messaging.MessageHandler)
	 */
	@Override
	public void subscribe(String messageId, MessageHandler handler) {
		idLock.writeLock().lock();

		if (!messageIdHandler.containsKey(messageId)) {
			messageIdHandler.put(messageId, new ArrayList<>());
		}

		messageIdHandler.get(messageId).add(handler);

		idLock.writeLock().unlock();
	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.msg2.IMessageProvider#unsubscribe(java.lang.String, net.bestia.zoneserver.messaging.MessageHandler)
	 */
	@Override
	public void unsubscribe(String messageId, MessageHandler handler) {
		idLock.writeLock().lock();

		if (messageIdHandler.containsKey(messageId)) {
			messageIdHandler.get(messageId).remove(handler);
		}

		idLock.writeLock().unlock();
	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.msg2.IMessageProvider#subscribe(java.util.function.Predicate, net.bestia.zoneserver.messaging.MessageHandler)
	 */
	@Override
	public void subscribe(Predicate<Message> predicate, MessageHandler handler) {

		predicateLock.writeLock().lock();

		predicateHandler.add(new Tuple(predicate, handler));

		predicateLock.writeLock().unlock();

	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.msg2.IMessageProvider#unsubscribe(java.util.function.Predicate, net.bestia.zoneserver.messaging.MessageHandler)
	 */
	@Override
	public void unsubscribe(Predicate<Message> predicate, MessageHandler handler) {

		predicateLock.writeLock().lock();

		predicateHandler.removeIf(x -> x.predicate == predicate && x.handler == handler);

		predicateLock.writeLock().unlock();

	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.msg2.IMessageProvider#handleMessage(net.bestia.messages.Message)
	 */
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
