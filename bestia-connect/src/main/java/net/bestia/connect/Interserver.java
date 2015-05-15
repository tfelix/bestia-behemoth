package net.bestia.connect;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class Interserver {

	private final static Logger log = LogManager.getLogger(Interserver.class);

	public static void main(String[] args) {

		log.info("Starting Bestia Behemoth Interserver...");

		// TODO Replace with build file version.
		log.info("Version v" + "0.0.1-alpha");

		Context context = ZMQ.context(1);

		Socket publisher = context.socket(ZMQ.PUB);
		// TODO Port wählbar machen.
		publisher.bind("tcp://*:9999");

		while (!Thread.currentThread().isInterrupted()) {
			// Write two messages, each with an envelope and content
			publisher.sendMore("A");
			publisher.send("We don't want to see this");
			publisher.sendMore("B");

			publisher.send("We would like to see this");
			log.info("Geht.");
		}
		publisher.close();
		context.term();

	}

}
