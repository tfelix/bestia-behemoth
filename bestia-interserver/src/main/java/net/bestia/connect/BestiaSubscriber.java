package net.bestia.connect;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;


public class BestiaSubscriber {
	

	public BestiaSubscriber(String connectString, String path) {
		
		Context context = ZMQ.context(1);
		Socket subscriber = context.socket(ZMQ.SUB);
		subscriber.connect(connectString);
		subscriber.subscribe(path.getBytes(ZMQ.CHARSET));
		
		while (!Thread.currentThread ().isInterrupted ()) {
		// Read envelope with address
		String address = subscriber.recvStr ();
		// Read message contents
		String contents = subscriber.recvStr ();
		System.out.println(address + " : " + contents);
		}
		subscriber.close ();
		context.term ();
	}
	
	public static BestiaSubscriber getMessageSubscriber() {
		return null;
	}
	
}
