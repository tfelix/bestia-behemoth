package net.bestia.interserver;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

/**
 * With the InterserverSubscriber it is possible to connect to the interserver
 * and listen to certain topics. If a message for this topic will be published a
 * callback via an InterserverListener will be issued.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
class InterserverZMQSubscriber implements InterserverSubscriber {
	
	private final Socket subscriber;
	private final String url;

	public InterserverZMQSubscriber(InterserverMessageHandler listener, String url, Context ctx) {
		subscriber = ctx.socket(ZMQ.SUB);
		
		this.url = url;
	}
	
	/* (non-Javadoc)
	 * @see net.bestia.interserver.InterserverSubscriber#connect()
	 */
	@Override
	public void connect() {
		subscriber.connect(url);
	}
	
	/* (non-Javadoc)
	 * @see net.bestia.interserver.InterserverSubscriber#disconnect()
	 */
	@Override
	public void disconnect() {
		subscriber.disconnect(url);
	}
	
	/* (non-Javadoc)
	 * @see net.bestia.interserver.InterserverSubscriber#subscribe(java.lang.String)
	 */
	@Override
	public void subscribe(String topic) {
		subscriber.subscribe(topic.getBytes());
	}
	
	/* (non-Javadoc)
	 * @see net.bestia.interserver.InterserverSubscriber#unsubscribe(java.lang.String)
	 */
	@Override
	public void unsubscribe(String topic) {
		subscriber.unsubscribe(topic.getBytes());
	}
}
