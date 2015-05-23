package net.bestia.websocket;

import java.io.IOException;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class InterserverConnection {

	private final String connectionStr;
	private final Context context;
	private final Socket subscriber;

	public InterserverConnection(String connectionStr) {
		this.connectionStr = connectionStr;
		this.context = ZMQ.context(1);
		this.subscriber = context.socket(ZMQ.SUB);
	}

	public void connect() throws IOException {
		subscriber.connect(connectionStr);
		
		subscribeToTopics();

		// Read envelope with address
		String address = subscriber.recvStr();
		// Read message contents
		String contents = subscriber.recvStr();
		System.out.println(address + " : " + contents);

	}

	private void subscribeToTopics() {
		subscriber.subscribe("B".getBytes());
	}

	public void disconnect() {
		subscriber.close();
		context.term();
	}

}
