package net.bestia.webserver;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LoginAuthReplyMessage.LoginState;

/**
 * Handles checks for login checks to the interserver in a blocking fashion.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class LoginCheckBlocker {

	private static final Logger log = LogManager.getLogger(LoginCheckBlocker.class);

	private Map<String, BlockingQueue<LoginAuthReplyMessage>> buffer = new HashMap<String, BlockingQueue<LoginAuthReplyMessage>>();
	private final BestiaConnectionProvider provider;

	/**
	 * Ctor.
	 * 
	 * @param bestiaConnectionProvider
	 */
	public LoginCheckBlocker(BestiaConnectionProvider bestiaConnectionProvider) {
		if (bestiaConnectionProvider == null) {
			throw new IllegalArgumentException("BestiaConnectionProvider can not be null.");
		}

		this.provider = bestiaConnectionProvider;
	}

	/**
	 * Is called with the reply from the loginserver.
	 * 
	 * @param msg
	 *            The message from the loginsserver.
	 */
	public void receivedAuthReplayMessage(LoginAuthReplyMessage msg) {

		final String requestId = msg.getRequestId();

		if (!buffer.containsKey(requestId)) {
			return;
		}

		try {
			synchronized (buffer) {
				// Offer the item for 1 second.
				BlockingQueue<LoginAuthReplyMessage> queue = buffer.get(requestId);
				if (queue == null) {
					return;
				}
				// Offer the item for 1 second.
				queue.offer(msg, 1, TimeUnit.SECONDS);
			}
		} catch (InterruptedException e) {
			log.debug("Offering for LoginAuthReply exeeded wait time.", e);
		}
	}

	/**
	 * Checks if the given account id has a correct login for the provided token
	 * (hash). Therefore the loginserver is asked. Since we need this decesion
	 * immediately this call will block for 30 seconds at max or until the anwer
	 * from the login server is received. The limit might be raised if the
	 * loginserver is rather busy.
	 * 
	 * @param accountId
	 *            The account ID to check login.
	 * @param token
	 *            The token to check against.
	 * @return TRUE if authenticated. FALSE otherwise.
	 */
	public boolean isAuthenticated(long accountId, String token) {

		if (token == null) {
			throw new IllegalArgumentException("Token can not be null.");
		}

		final LoginAuthMessage msg = new LoginAuthMessage(accountId, token);

		final String requestId = msg.getRequestId();

		// We need to utilize a blocking queue with capacity 1 to get blocking
		// ability.
		final BlockingQueue<LoginAuthReplyMessage> queue = new ArrayBlockingQueue<>(1);

		synchronized (buffer) {
			buffer.put(requestId, queue);
		}

		// Send message.
		try {
			provider.publishInterserver(msg);
		} catch (IOException e) {
			log.debug("Could not send LoginAuth message.", e);

			synchronized (buffer) {
				buffer.remove(requestId);
			}

			return false;
		}

		try {
			final LoginAuthReplyMessage replyMsg = queue.poll(30, TimeUnit.SECONDS);

			// Might be null if we hid a timeout.
			if (replyMsg == null) {
				return false;
			}

			return replyMsg.getLoginState() == LoginState.AUTHORIZED;
		} catch (InterruptedException e) {
			log.debug("Timeout while waiting for LoginAuthReply message.", e);
			return false;
		} finally {
			synchronized (buffer) {
				buffer.remove(requestId);
			}
		}
	}

}
