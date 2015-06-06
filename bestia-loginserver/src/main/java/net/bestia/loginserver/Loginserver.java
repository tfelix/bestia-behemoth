package net.bestia.loginserver;

import java.io.IOException;

import net.bestia.interserver.InterserverConnectionFactory;
import net.bestia.interserver.InterserverMessageHandler;
import net.bestia.interserver.InterserverPublisher;
import net.bestia.interserver.InterserverSubscriber;
import net.bestia.loginserver.authenticator.AuthState;
import net.bestia.loginserver.authenticator.Authenticator;
import net.bestia.messages.LoginMessage;
import net.bestia.messages.LoginReplyMessage;
import net.bestia.messages.LoginReplyMessage.LoginState;
import net.bestia.messages.Message;
import net.bestia.model.Account;
import net.bestia.util.BestiaConfiguration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The loginserver connects to the interserver and listens for auth.login
 * messages. With these messages the webserver will authenticate new incoming
 * connections. For this purpose the loginserver also access the database and
 * checks the current token. But the loginserver also provides means of login
 * methods for setting exactly this login token.
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
		final int publishPort = config.getIntProperty("inter.publishPort");
		final int listenPort = config.getIntProperty("inter.listenPort");

		InterserverConnectionFactory conFactory = new InterserverConnectionFactory(
				1, interUrl, listenPort, publishPort);

		this.publisher = conFactory.getPublisher();
		this.subscriber = conFactory.getSubscriber(this);
	}

	public void start() throws Exception {
		log.info("Starting the Bestia Loginserver [{}]...");

		// Connect to the interserver.
		publisher.connect();
		subscriber.connect();

		subscriber.subscribe("login");

		log.info("Loginserver started.");
	}

	@Override
	public void onMessage(Message msg) {
		// Just process login authentication messages.
		if (!(msg instanceof LoginMessage)) {
			return;
		}
		LoginMessage loginMsg = (LoginMessage) msg;
		log.debug("Received login auth request: {}", loginMsg.toString());
		
		// Get the account from the database. 
		// TODO
		loginMsg.getAccountId();
		
		Account account = null;
		LoginReplyMessage loginReplyMsg = new LoginReplyMessage(loginMsg);
		if(account.getLoginToken().equals(loginMsg.getToken())) {
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
	
	/**
	 * Authenticate a user via the provided authenticator.
	 * 
	 * @param authenticator
	 */
	public AuthState authenticate(Authenticator authenticator) {
		return authenticator.authenticate(this);
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
