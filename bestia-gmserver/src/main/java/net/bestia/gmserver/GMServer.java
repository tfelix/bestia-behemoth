package net.bestia.gmserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import net.bestia.gmserver.servlets.LoginServlet;
import net.bestia.gmserver.servlets.MyVaadinServlet;

/**
 * The GM Server will provide an way to access the bestia game database to add,
 * change and edit account details for the players. The interface is provided
 * via VAADIN and an embedded jetty.
 * 
 * @author Thomas Felix <thomas.felix@tfleix.de>
 *
 */
public final class GMServer {

	private static final Logger LOG = LogManager.getLogger(GMServer.class);

	public boolean start() {

		final Server server = new Server(8080);

		final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		server.setHandler(context);

		context.addServlet(new ServletHolder(new MyVaadinServlet()), "/*");
		context.addServlet(new ServletHolder(new LoginServlet()), "/login/*");

		try {
			server.start();
		} catch (Exception e) {
			LOG.fatal("Could not start GM-Server.", e);
			stop();
			return false;
		}

		return true;
	}

	public void stop() {

	}
}
