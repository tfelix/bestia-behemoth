package net.bestia.core.game.worker;

import java.util.concurrent.BlockingQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.core.connection.BestiaConnectionManager;
import net.bestia.core.message.Message;

/**
 * This worker is responsible for taking messages which are waiting
 * in the message queue, take them and send them to the clients.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public final class MessageWorker implements Runnable {
	private static int totalN = 0;
	private int N = 0;
	private static final Logger log = LogManager.getLogger(MessageWorker.class);
	
	private BlockingQueue<Message> queue;
	private boolean isRunning = true;
	private BestiaConnectionManager connection;
	
	public MessageWorker(BestiaConnectionManager connection, BlockingQueue<Message> queue) {
		if(connection == null) {
			throw new IllegalArgumentException("Connection can not be null.");
		}
		if(queue == null) {
			throw new IllegalArgumentException("Queue can not be null.");
		}
		N = ++totalN;
		this.queue = queue;
		this.connection = connection;
	}
	
	/**
	 * Stops the current running thread.
	 */
	public void Abort() {
		isRunning = false;
		// TODO Thread interrupt should be called here to go out of the blocking queue.
	}

	@Override
	public void run() {
		log.debug("MessageWorker #{} started.", N);
		while(isRunning) {
			try {
				Message msg = queue.take();
				log.trace("Sending msg {} from thread {}.", msg, N);
				connection.sendMessage(msg);
			} catch(InterruptedException ex) {
				// no op.
			}
		}
		log.debug("MessageWorker #{} stopped.", N);
	}

}
