package net.bestia.loginserver.rest;

import java.net.URI;
import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.ws.rs.core.UriBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.servlet.ServletContainer;

import net.bestia.interserver.InterserverPublisher;
import net.bestia.util.BestiaConfiguration;

/**
 * Provides RESTful services for the bestia game. Can and should be used to
 * interact externally with the game. Could be extended to access game
 * statistics etc. Currently it provides APIs for registering accounts, changing
 * passwords etc.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class RestServer {

	// TODO Das hier noch konfiguruerbar machen!
	private static final Logger LOG = LogManager.getLogger(RestServer.class);
	private final int port;
	private static final String HOSTNAME = "localhost";

	private static InterserverPublisher insterserverPublisher;

	private Server jettyServer;

	public RestServer(BestiaConfiguration config, InterserverPublisher publisher) {
		if (config == null) {
			throw new IllegalArgumentException("Config can not be null.");
		}

		port = config.getIntProperty("login.restPort");

		RestServer.insterserverPublisher = publisher;
	}

	public boolean start() {
		LOG.info("Starting Bestia RESTful Server API...");
		try {
			jettyServer = new Server();

			// Server options.
			jettyServer.setStopAtShutdown(true);

			// HTTP Configuration.
			final HttpConfiguration config = new HttpConfiguration();
			config.setSendServerVersion(false);

			// Server connector.
			final ServerConnector http = new ServerConnector(jettyServer, new HttpConnectionFactory(config));
			http.setPort(port);
			jettyServer.addConnector(http);

			// Handler structure.
			final HandlerCollection handlers = new HandlerCollection();
			final ContextHandlerCollection contexts = new ContextHandlerCollection();
			handlers.setHandlers(new Handler[] { contexts, new DefaultHandler() });

			final ServletContextHandler servletContext = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
			servletContext.setContextPath("/");

			final ServletHolder jerseyServlet = servletContext.addServlet(ServletContainer.class, "/*");
			// jerseyServlet.setInitOrder(0);
			// Tells the Jersey Servlet which REST service/class to load.
			jerseyServlet.setInitParameter("jersey.config.server.provider.classnames",
					AccountApi.class.getCanonicalName());

			contexts.addHandler(servletContext);

			servletContext.addFilter(AddCORSFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));

			jettyServer.setHandler(handlers);

			jettyServer.start();

			LOG.info("Bestia RESTful server API started.");
			LOG.debug("WADL available at: {}application.wadl", getURI());
		} catch (Exception ex) {
			LOG.warn("Could not start RESTful Server: ", ex);
			return false;
		}
		return true;
	}

	/**
	 * Gets the interserver publisher. It is static so the servlets can get it.
	 * 
	 * @return
	 */
	static InterserverPublisher getInsterserverPublisher() {
		return insterserverPublisher;
	}

	private URI getURI() {
		return UriBuilder.fromUri("http://" + HOSTNAME + "/")
				.port(port)
				.build();
	}

	public void stop() {
		try {
			jettyServer.stop();
			jettyServer.join();
		} catch (Exception e) {
			LOG.error("Error while stopping Jetty:", e);
		}
		jettyServer.destroy();
	}

}
