package net.bestia.loginserver.rest;

import java.io.IOException;
import java.net.URI;

import javax.ws.rs.core.UriBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
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

	public boolean start() {
		log.info("Starting Bestia RESTful Server API...");
		try {
			ServletContextHandler context = new ServletContextHandler(
					ServletContextHandler.SESSIONS);
			context.setContextPath("/");

			Server jettyServer = new Server(PORT);
			jettyServer.setHandler(context);

			ServletHolder jerseyServlet = context.addServlet(
					ServletContainer.class, "/*");
			jerseyServlet.setInitOrder(0);

			// Tells the Jersey Servlet which REST service/class to load.
			jerseyServlet.setInitParameter(
					"jersey.config.server.provider.classnames",
					AccountApi.class.getCanonicalName());

			try {
				jettyServer.start();
				jettyServer.join();
			} finally {
				jettyServer.destroy();
			}


			log.info("Bestia RESTful Server API started.");
			log.info("WADL available at: {}application.wadl", getURI());
		} catch (IOException ex) {
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

	}

}
