package net.bestia.connect;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class Interserver {

	private final static Logger log = LogManager.getLogger(Interserver.class);

	public static class SenderThread extends Thread {
		
		private Context ctx;
		
		public SenderThread(Context ctx) {
			// TODO Auto-generated constructor stub
			this.ctx = ctx;
		}

		@Override
		public void run() {
			// Prepare our context and publisher
			Socket publisher = ctx.socket(ZMQ.PUB);
			publisher.bind("tcp://*:9999");
			
			while (true) {
				// Write two messages, each with an envelope and content
				publisher.sendMore("web/onMessage");
				publisher.send("Hello World!");
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
			//publisher.close();
			//context.term();
		}
	}

	public static void main(String[] args) {

		final InterserverConfig config = new InterserverConfig();

		log.info("Starting Bestia Behemoth Interserver...");

		// TODO Replace with build file version.
		log.info("Version v" + "0.0.1-alpha");

		// TODO hier number of threads einfügen.
		final Context context = ZMQ.context(1);

		/*final Socket publisher = context.socket(ZMQ.PUB);
		publisher.bind("tcp://*:" + config.getPort());

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				log.info("Terminating the Interserver...");
				publisher.close();
				context.term();
			}
		});*/
		
		// Start pub thread.
		SenderThread t = new SenderThread(context);
		t.start();

		// Should be done by zone servers.
		WebserverSubscriber test = new WebserverSubscriber(config, context);
		test.subscribe();
		test.test();

		log.info("Interserver started.");
	}

}
