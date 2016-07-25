package net.bestia.loginserver;

import java.io.IOException;

import net.bestia.interserver.InterserverConnectionFactory;
import net.bestia.interserver.InterserverMessageHandler;
import net.bestia.interserver.InterserverPublisher;
import net.bestia.interserver.InterserverSubscriber;
import net.bestia.loginserver.authenticator.AuthState;
import net.bestia.loginserver.authenticator.Authenticator;
import net.bestia.loginserver.authenticator.LoginTokenAuthenticator;
import net.bestia.loginserver.rest.RestServer;
import net.bestia.messages.Message;
import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LoginAuthReplyMessage.LoginState;
import net.bestia.util.BestiaConfiguration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;

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

	private static final Logger LOG = LogManager.getLogger(Loginserver.class);

	// Metrics
	private final Counter loginMessageMetric = Monitors.newCounter("LoginMessages");
	private final Counter acceptedLoginMessageMetric = Monitors.newCounter("AcceptedLoginMessages");
	private final Counter deniedLoginMessageMetric = Monitors.newCounter("DeniedLoginMessages");

	private RestServer restServer;

	private final InterserverConnectionFactory conFactory;
	private InterserverPublisher publisher;
	private InterserverSubscriber subscriber;

	private final BestiaConfiguration config;

	/**
	 * Class which starts and runs the bestia web front server.
	 * 
	 * @param config
	 *            Loaded configuration file.
	 */
	public Loginserver(BestiaConfiguration config) {

		if (config == null || !config.isLoaded()) {
			throw new IllegalArgumentException("Config was null or not loaded.");
		}

		this.config = config;

		// Create the publish url.
		final String interUrl = config.getProperty("inter.domain");
		// Since this is from "our" perspective, the listening port of the
		// interserver is our publishing port.
		final int listenPort = config.getIntProperty("inter.publishPort");
		final int publishPort = config.getIntProperty("inter.listenPort");

		this.conFactory = new InterserverConnectionFactory(1, interUrl, listenPort, publishPort);

		try {
			this.publisher = conFactory.getPublisher();
			this.subscriber = conFactory.getSubscriber(this);
		} catch (IOException ex) {
			// Fatal. Could not create sockets.
			System.exit(1);

		}

		this.restServer = new RestServer(config, this.publisher);

		Monitors.registerObject("Loginserver", this);
	}

	/**
	 * Starts the Loginserver.
	 * 
	 * @return {@code TRUE} if started. {@code FALSE} otherwise.
	 */
	public boolean start() {
		LOG.info(config.getVersion());
		LOG.info("Starting the Bestia Loginserver...");

		// Connect to the interserver.
		try {
			publisher.connect();
			//subscriber.connect();
			subscriber.subscribe("login");
		} catch (IOException ex) {
			LOG.error("Loginserver could not start.", ex);
			stop();
			return false;
		}

		if (!restServer.start()) {
			LOG.error("Loginserver (web/rest) could not start.");
			stop();
			return false;
		}

		LOG.info("Loginserver started.");
		return true;
	}

	/**
	 * Stops the Loginserver.
	 */
	public void stop() {
		LOG.info("Stopping the Bestia Loginserver...");
		restServer.stop();
		conFactory.shutdown();

		LOG.info("Loginserver stopped.");
	}

	@Override
	public void onMessage(Message msg) {
		// Just process login authentication messages.
		if (!(msg instanceof LoginAuthMessage)) {
			return;
		}

		final LoginAuthMessage loginMsg = (LoginAuthMessage) msg;
		LOG.debug("Received login auth request: {}", loginMsg.toString());
		loginMessageMetric.increment();

		final Authenticator tokenAuth = new LoginTokenAuthenticator(loginMsg.getAccountId(), loginMsg.getToken());

		final LoginAuthReplyMessage loginReplyMsg = new LoginAuthReplyMessage(loginMsg);
		loginReplyMsg.setAccountId(loginMsg.getAccountId());
		if (tokenAuth.authenticate() == AuthState.AUTHENTICATED) {
			LOG.info("Connection with account id: {}, token: {}, state: AUTHORIZED", loginMsg.getAccountId(),
					loginMsg.getToken());
			loginReplyMsg.setLoginState(LoginState.AUTHORIZED);

			acceptedLoginMessageMetric.increment();
		} else {
			LOG.info("Connection with account id: {}, token: {}, state: DENIED", loginMsg.getAccountId(),
					loginMsg.getToken());
			loginReplyMsg.setLoginState(LoginState.DENIED);

			deniedLoginMessageMetric.increment();
		}

		try {
			publisher.publish(loginReplyMsg);
		} catch (IOException e) {
			LOG.error("Could not send LoginReplyMessage", e);
		}
	}
}
