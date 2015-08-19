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
	private static final Logger log = LogManager.getLogger(RestServer.class);
	private static final int PORT = 8090;
	private static final String HOSTNAME = "localhost";

	private Server jettyServer;

	public boolean start() {
		log.info("Starting Bestia RESTful Server API...");
		try {
			jettyServer = new Server();

			// Server options.
			jettyServer.setDumpAfterStart(true);
			jettyServer.setDumpBeforeStop(true);
			jettyServer.setStopAtShutdown(true);

			// HTTP Configuration.
			HttpConfiguration config = new HttpConfiguration();
			config.setSendServerVersion(false);

			// Server connector.
			ServerConnector http = new ServerConnector(jettyServer,
					new HttpConnectionFactory(config));
			http.setPort(PORT);
			jettyServer.addConnector(http);

			// Handler structure.
			HandlerCollection handlers = new HandlerCollection();
			ContextHandlerCollection contexts = new ContextHandlerCollection();
			handlers.setHandlers(new Handler[] { contexts, new DefaultHandler() });

			ServletContextHandler servletContext = new ServletContextHandler(
					ServletContextHandler.SESSIONS);
			servletContext.setContextPath("/");

			ServletHolder jerseyServlet = servletContext.addServlet(
					ServletContainer.class, "/*");
			jerseyServlet.setInitOrder(0);
			// Tells the Jersey Servlet which REST service/class to load.
			jerseyServlet.setInitParameter(
					"jersey.config.server.provider.classnames",
					AccountApi.class.getCanonicalName());

			contexts.addHandler(servletContext);

			servletContext.addFilter(AddCORSFilter.class, "/*",
					EnumSet.of(DispatcherType.REQUEST));

			jettyServer.setHandler(handlers);

			jettyServer.start();

			log.info("Bestia RESTful Server API started.");
			log.info("WADL available at: {}application.wadl", getURI());
		} catch (Exception ex) {
			log.warn("Could not start RESTful Server: ", ex);
			return false;
		}
		return true;
	}

	private URI getURI() {
		return UriBuilder.fromUri("http://" + HOSTNAME + "/").port(PORT)
				.build();
	}

	public void stop() {
		try {
			jettyServer.stop();
			jettyServer.join();
		} catch (Exception e) {
			log.error("Error while stopping Jetty:", e);
		}
		jettyServer.destroy();
	}

}
