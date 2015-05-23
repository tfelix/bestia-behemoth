package net.bestia.connect;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

/**
 * This class subscribes itself to messages from the webserver, listens to them and executed them.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class WebserverSubscriber {

	private final Socket subscriber;
	private final InterserverConfig config;

	public WebserverSubscriber(InterserverConfig config, Context context) {
		
		this.config = config;
		this.subscriber = context.socket(ZMQ.SUB);
	}
	
	public void subscribe() {
		subscriber.connect("tcp://localhost:"+config.getPort());		
        subscriber.subscribe("web/onMessage".getBytes());
	}
	
	public void test() {
		while (true) {
            // Read envelope with address
            String address = subscriber.recvStr ();
            // Read message contents
            String contents = subscriber.recvStr ();
            System.out.println(address + " : " + contents);
        }
	}
}
