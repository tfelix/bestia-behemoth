package net.bestia.loginserver;

import java.io.IOException;

import net.bestia.interserver.InterserverConnectionFactory;
import net.bestia.interserver.InterserverMessageHandler;
import net.bestia.interserver.InterserverPublisher;
import net.bestia.interserver.InterserverSubscriber;
import net.bestia.loginserver.authenticator.AuthState;
import net.bestia.loginserver.authenticator.Authenticator;
import net.bestia.loginserver.authenticator.DebugAuthenticator;
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

		InterserverConnectionFactory conFactory = new InterserverConnectionFactory(1, interUrl, listenPort, publishPort);

		this.publisher = conFactory.getPublisher();
		this.subscriber = conFactory.getSubscriber(this);
	}

	public void start() throws Exception {
		log.info("Starting the Bestia Loginserver...");

		// Connect to the interserver.
		publisher.connect();
		subscriber.connect();

		subscriber.subscribe("login");

		log.info("Loginserver started.");
	}

	@Override
	public void onMessage(Message msg) {
		// Just process login authentication messages.
		if (!(msg instanceof LoginAuthMessage)) {
			return;
		}
		LoginAuthMessage loginMsg = (LoginAuthMessage) msg;
		log.debug("Received login auth request: {}", loginMsg.toString());

		// Authenticator tokenAuth = new LoginTokenAuthenticator(loginMsg.getAccountId(), loginMsg.getToken());
		Authenticator tokenAuth = new DebugAuthenticator();

		final LoginAuthReplyMessage loginReplyMsg = new LoginAuthReplyMessage(loginMsg);
		loginReplyMsg.setAccountId(msg.getAccountId());
		if (tokenAuth.authenticate() == AuthState.AUTHENTICATED) {
			loginReplyMsg.setLoginState(LoginState.AUTHORIZED);
		} else {
			loginReplyMsg.setLoginState(LoginState.DENIED);
		}

		try {
			publisher.publish(loginReplyMsg);
		} catch (IOException e) {
			log.error("Could not send LoginReplyMessage", e);
		}

	}

	@Override
	public void connectionLost() {
		log.warn("Connection to the interserver was lost.");
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
		try {
			server.start();
		} catch (Exception ex) {
			log.fatal("Server could not start.", ex);
			System.exit(1);
		}
	}
}
