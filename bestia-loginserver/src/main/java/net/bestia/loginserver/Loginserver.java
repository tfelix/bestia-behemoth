package net.bestia.loginserver;

import java.io.IOException;

import net.bestia.interserver.InterserverConnectionFactory;
import net.bestia.interserver.InterserverMessageHandler;
import net.bestia.interserver.InterserverPublisher;
import net.bestia.interserver.InterserverSubscriber;
import net.bestia.loginserver.authenticator.AuthState;
import net.bestia.loginserver.authenticator.Authenticator;
import net.bestia.loginserver.authenticator.DebugAuthenticator;
import net.bestia.loginserver.authenticator.LoginTokenAuthenticator;
import net.bestia.messages.LoginAuthMessage;
import net.bestia.messages.LoginAuthReplyMessage;
import net.bestia.messages.LoginAuthReplyMessage.LoginState;
import net.bestia.messages.Message;
import net.bestia.util.BestiaConfiguration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The loginserver connects to the interserver and listens for auth.login messages. With these messages the webserver
 * will authenticate new incoming connections. For this purpose the loginserver also access the database and checks the
 * current token. But the loginserver also provides means of login methods for setting exactly this login token.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public final class Loginserver implements InterserverMessageHandler {

	private static final Logger log = LogManager.getLogger(Loginserver.class);

	private final InterserverConnectionFactory conFactory;
	private final InterserverPublisher publisher;
	private final InterserverSubscriber subscriber;

	/**
	 * Class which starts and runs the bestia web front server.
	 * 
	 * @param config
	 *            Loaded configuration file.
	 */
	public Loginserver(BestiaConfiguration config) {

		// Create the publish url.
		final String interUrl = config.getProperty("inter.domain");
		// Since this is from "our" perspective, the listening port of the interserver is our publishing port.
		final int listenPort = config.getIntProperty("inter.publishPort");
		final int publishPort = config.getIntProperty("inter.listenPort");

		this.conFactory = new InterserverConnectionFactory(1, interUrl, listenPort, publishPort);

		this.publisher = conFactory.getPublisher();
		this.subscriber = conFactory.getSubscriber(this);
	}

	/**
	 * Starts the Loginserver.
	 * 
	 * @return {@code TRUE} if started. {@code FALSE} otherwise.
	 */
	public boolean start() {
		log.info("Starting the Bestia Loginserver...");

		// Connect to the interserver.
		try {
			publisher.connect();
			subscriber.connect();
			subscriber.subscribe("login");
		} catch (IOException ex) {
			log.error("Loginserver could not start.", ex);
			stop();
			return false;
		}

		log.info("Loginserver started.");
		return true;
	}

	/**
	 * Stops the Loginserver.
	 */
	public void stop() {
		if (subscriber != null) {
			subscriber.disconnect();
		}

		if (publisher != null) {
			publisher.disconnect();
		}

		conFactory.shutdown();
	}

	@Override
	public void onMessage(Message msg) {
		// Just process login authentication messages.
		if (!(msg instanceof LoginAuthMessage)) {
			return;
		}
		LoginAuthMessage loginMsg = (LoginAuthMessage) msg;
		log.debug("Received login auth request: {}", loginMsg.toString());

		Authenticator tokenAuth = new LoginTokenAuthenticator(loginMsg.getAccountId(), loginMsg.getToken());

		final LoginAuthReplyMessage loginReplyMsg = new LoginAuthReplyMessage(loginMsg);
		loginReplyMsg.setAccountId(msg.getAccountId());
		if (tokenAuth.authenticate() == AuthState.AUTHENTICATED) {
			log.info("Connection with account id: {}, token: {}, state: AUTHORIZED", loginMsg.getAccountId(), loginMsg.getToken());
			loginReplyMsg.setLoginState(LoginState.AUTHORIZED);
		} else {
			log.info("Connection with account id: {}, token: {}, state: DENIED", loginMsg.getAccountId(), loginMsg.getToken());
			loginReplyMsg.setLoginState(LoginState.DENIED);
		}

		try {
			publisher.publish(loginReplyMsg);
		} catch (IOException e) {
			log.error("Could not send LoginReplyMessage", e);
		}
	}

	public static void main(String[] args) {

		final BestiaConfiguration config = new BestiaConfiguration();
		try {
			config.load();
		} catch (IOException ex) {
			log.fatal("Could not load configuration file. Exiting.", ex);
			System.exit(1);
		}

		final Loginserver server = new Loginserver(config);

		if (!server.start()) {
			System.exit(1);
		}

		// Cancel the loginserver gracefully when the VM shuts down. Does not
		// work properly on windows machines.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				server.stop();
			}
		});
	}
}
