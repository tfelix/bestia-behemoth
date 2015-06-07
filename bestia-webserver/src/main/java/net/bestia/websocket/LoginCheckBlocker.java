package net.bestia.websocket;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import net.bestia.messages.LoginAuthMessage;
import net.bestia.messages.LoginAuthReplyMessage;
import net.bestia.messages.LoginAuthReplyMessage.LoginState;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles checks for login checks to the interserver in a blocking fashion.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class LoginCheckBlocker {

	private static final Logger log = LogManager.getLogger(LoginCheckBlocker.class);

	private Map<String, BlockingQueue<LoginAuthReplyMessage>> blockingQueue = new HashMap<String, BlockingQueue<LoginAuthReplyMessage>>();
	private final BestiaConnectionProvider provider;

	public LoginCheckBlocker(BestiaConnectionProvider bestiaConnectionProvider) {
		this.provider = bestiaConnectionProvider;
	}

	public void receivedAuthReplayMessage(LoginAuthReplyMessage msg) {

		final String requestId = msg.getRequestId();

		if (!blockingQueue.containsKey(requestId)) {
			return;
		}

		try {
			synchronized (blockingQueue) {
				// Offer the item for 1 second.
				BlockingQueue<LoginAuthReplyMessage> queue = blockingQueue.get(requestId);
				if (queue == null) {
					return;
				}
				// Offer the item for 1 second.
				queue.offer(msg, 1, TimeUnit.SECONDS);
			}
		} catch (InterruptedException e) {
			log.trace("Offering for LoginAuthReply exeeded wait time.", e);
		}
	}

	public boolean isAuthenticated(long accountId, String token) {

		LoginAuthMessage msg = new LoginAuthMessage(accountId, token);

		final String requestId = msg.getRequestId();
		final BlockingQueue<LoginAuthReplyMessage> queue = new ArrayBlockingQueue<>(1);

		synchronized (blockingQueue) {
			blockingQueue.put(requestId, queue);
		}

		// Send message.
		try {
			provider.publishInterserver(msg);
		} catch (IOException e) {
			log.debug("Could not send LoginAuth message.", e);

			synchronized (blockingQueue) {
				blockingQueue.remove(requestId);
			}

			return false;
		}

		try {
			LoginAuthReplyMessage replyMsg = queue.poll(3, TimeUnit.SECONDS);
			
			// Might be null if we hid a timeout.
			if(replyMsg == null) {
				return false;
			}
			
			return replyMsg.getLoginState() == LoginState.AUTHORIZED;
		} catch (InterruptedException e) {
			log.debug("Timeout while waiting for LoginAuthReply message.", e);
			return false;
		} finally {
			synchronized (blockingQueue) {
				blockingQueue.remove(requestId);
			}
		}
	}

}
