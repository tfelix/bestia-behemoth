package net.bestia.interserver;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;

/**
 * Abstract factory class implementation which is used to create connection to the interserver. This class could be a
 * candidate for refactoring. To support multiple messaging backbones just implement this as an abstract factory.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InterserverConnectionFactory {

	private final Context context;

	public InterserverConnectionFactory(int numThreads) {

		context = ZMQ.context(numThreads);
	}

	/**
	 * Returns a subscriber which can subscribe to certain topic to a interserver.
	 * 
	 * @param handler
	 *            Callback which will be used if an message is incoming.
	 * @param url
	 * @return
	 */
	public InterserverSubscriber getSubscriber(InterserverMessageHandler handler, String url) {
		return new InterserverZMQSubscriber(handler, url, context);
	}

	public InterserverPublisher getPublisher(String url) {
		return new InterserverZMQPublisher(url, context);
	}
}
