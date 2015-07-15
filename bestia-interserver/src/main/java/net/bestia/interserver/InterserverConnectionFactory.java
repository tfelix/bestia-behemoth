package net.bestia.interserver;

import java.util.ArrayList;
import java.util.List;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;

/**
 * Factory class implementation which is used to create connection to the interserver. This class could be a
 * candidate for refactoring. To support multiple messaging backbones just implement this as an abstract factory.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InterserverConnectionFactory {

	private final Context context;
	private final String subscriberUrl;
	private final String publishUrl;
	
	private final List<InterserverSubscriber> spawnedSubsciber = new ArrayList<>();
	private final List<InterserverPublisher> spawnedPublisher = new ArrayList<>();

	public InterserverConnectionFactory(int numThreads, String url, int listenPort, int publishPort) {

		context = ZMQ.context(numThreads);

		subscriberUrl = "tcp://" + url + ":" + listenPort;
		publishUrl = "tcp://" + url + ":" + publishPort;
	}

	/**
	 * Returns a subscriber which can subscribe to certain topic to a interserver.
	 * 
	 * @param handler
	 *            Callback which will be used if an message is incoming.
	 * @return Subscriber which can be used to receive asyncrounous data from the interserver.
	 */
	public InterserverSubscriber getSubscriber(InterserverMessageHandler handler) {
		
		final InterserverSubscriber sub = new InterserverZMQSubscriber(handler, subscriberUrl, context);
		spawnedSubsciber.add(sub);
		return sub;
	}

	/**
	 * Returns a publisher to send data to the interserver.
	 * 
	 * @return An publisher which is able to send data to the interserver.
	 */
	public InterserverPublisher getPublisher() {
		
		final InterserverPublisher pub =  new InterserverZMQPublisher(publishUrl, context);
		spawnedPublisher.add(pub);
		return pub;
	}

	/**
	 * The factory might hold a reference to a socket-context of whatsoever means. To properly shut down this method
	 * should be called to do some cleanup.
	 */
	public void shutdown() {
		
		for(InterserverPublisher p : spawnedPublisher) {
			p.disconnect();
		}
		
		context.term();
		
		for(InterserverSubscriber s : spawnedSubsciber) {
			s.disconnect();
		}
		
		spawnedPublisher.clear();
		spawnedSubsciber.clear();
	}
}
