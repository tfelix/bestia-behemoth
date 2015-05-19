package net.bestia.connect;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class Interserver {

	private final static Logger log = LogManager.getLogger(Interserver.class);
	private final static int PORT = 9999;

	private static class MQThread extends Thread {
		
		public AtomicBoolean isRunning = new AtomicBoolean(true);
		private final static String NAME = "MQTTHREAD";
		
		public MQThread() {
			this.setName(NAME);
		}
		
		public void run() {

			final Context context = ZMQ.context(1);

			final Socket publisher = context.socket(ZMQ.PUB);
			publisher.bind("tcp://*:" + PORT);

			while (isRunning.get()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// no op.
				}
			}
			
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					log.info("Terminating the Interserver...");
					publisher.close();
					context.term();
				}
			});
		}
	};

	public static void main(String[] args) {

		log.info("Starting Bestia Behemoth Interserver...");

		// TODO Replace with build file version.
		log.info("Version v" + "0.0.1-alpha");
		
		final MQThread mqt = new MQThread();
		mqt.start();
		
		log.info("Interserver started on: .");
	}

}
