package net.bestia.connect;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.util.concurrent.atomic.AtomicBoolean;

import net.bestia.util.BestiaConfiguration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class Interserver {

	private final static Logger log = LogManager.getLogger(Interserver.class);

	public static class RequestThread extends Thread {

		private final Context ctx;
		private final BestiaConfiguration config;
		private Socket responder;
		private final String publishUrl;
		private AtomicBoolean isRunning = new AtomicBoolean(true);

		public RequestThread(String publishUrl, Context ctx, BestiaConfiguration config) {
			this.ctx = ctx;
			this.publishUrl = publishUrl;
			this.config = config;

			this.setName("AnnounceRequestThread");
		}

		@Override
		public void run() {
			responder = ctx.socket(ZMQ.REP);
			responder.bind("tcp://*:" + config.getProperty("inter.announcePort"));

			while (isRunning.get()) {

				// Wait for next request from the client
				byte[] msg = null;
				try {
					log.debug("Interserver is waiting for announcement.");
					msg = responder.recv(0);
				} catch (ClosedSelectorException ex) {
					// Socket was closed by another thread. nop.
					log.trace("Announcement channel shutdown.", ex);
				}

				try {
					AnnounceWebserverMessage request = (AnnounceWebserverMessage) ObjectSerializer
							.deserializeObject(msg);

					// At the moment there can not be much wrong with the request.
					log.info("Webserver [{}] joins the bestia network.", request.getName());
					
					// Respond with an apropriate message.
					AnnounceWebserverMessage response = AnnounceWebserverMessage.getReplyMessage("interserver", publishUrl);
					final byte[] reply = ObjectSerializer.serializeObject(response);
					responder.send(reply, 0);

				} catch (ClassNotFoundException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		public void cancel() {
			responder.close();
			isRunning.set(false);
		}
	}

	private RequestThread requestThread;
	private BestiaConfiguration config;
	private Context context;

	public Interserver() {
		config = new BestiaConfiguration();
	}

	/**
	 * Starts the interserver.
	 */
	public void start() {
		log.info("Starting Bestia Behemoth Interserver...");
		log.info("Version v" + "0.0.1-alpha");

		try {
			config.load();
		} catch (IOException ex) {
			log.fatal("Could not start the interserver.", ex);
			System.exit(1);
		}
		context = ZMQ.context(config.getIntProperty("inter.threads"));

		// ===== PUB SUB SERVER =====
		final Socket publisher = context.socket(ZMQ.PUB);
		publisher.bind("tcp://*:" + config.getProperty("inter.publishPort"));
		// TODO hier noch was besseres ausdenken wenn das auf verschiedenen servern läuft.
		final String subscriberUrl = "tcp://localhost:" + config.getProperty("inter.publishPort");

		// ===== REQ REP SERVER =====
		requestThread = new RequestThread(subscriberUrl, context, config);
		requestThread.start();

		log.info("Interserver started.");

	}

	/**
	 * Stops the interserver.
	 */
	public void stop() {
		log.info("Stopping the Interserver...");

		requestThread.cancel();
		// publisher.close();

		context.term();
	}

	public static void main(String[] args) {
		final Interserver interserver = new Interserver();
		interserver.start();

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				interserver.stop();
			}
		});
	}

}
