package net.bestia.interserver;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;

/**
 * Abstract factory class implementation which is used to create connection to
 * the interserver. This class could be a candidate for refactoring. To support
 * multiple messaging backbones just implement this as an abstract factory.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InterserverConnectionFactory {

	private final Context context;
	private final String subscriberUrl;
	private final String publishUrl;

	public InterserverConnectionFactory(int numThreads, String url,
			int listenPort, int publishPort) {

		context = ZMQ.context(numThreads);

		subscriberUrl = "tcp://" + url + ":" + listenPort;
		publishUrl = "tcp://" + url + ":" + publishPort;
	}

	/**
	 * Returns a subscriber which can subscribe to certain topic to a
	 * interserver.
	 * 
	 * @param handler
	 *            Callback which will be used if an message is incoming.
	 * @return Subscriber which can be used to receive assyncrounous data from
	 *         the interserver.
	 */
	public InterserverSubscriber getSubscriber(InterserverMessageHandler handler) {
		return new InterserverZMQSubscriber(handler, subscriberUrl, context);
	}

	/**
	 * Returns a publisher to send data to the interserver.
	 * 
	 * @return An publisher which is able to send data to the interserver.
	 */
	public InterserverPublisher getPublisher() {
		return new InterserverZMQPublisher(publishUrl, context);
	}
}
