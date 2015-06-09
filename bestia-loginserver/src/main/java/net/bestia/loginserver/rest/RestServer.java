package net.bestia.loginserver.rest;

import java.io.IOException;
import java.net.URI;

import javax.ws.rs.core.UriBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.net.httpserver.HttpServer;

/**
 * Provides RESTful services for the bestia game.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@SuppressWarnings("restriction")
public class RestServer {
	
	private static final Logger log = LogManager.getLogger(RestServer.class);
	private static final int PORT = 8080;
	private static final String HOSTNAME = "localhost";

	public boolean start() {
		log.info("Starting Bestia RESTful Server API...");
		try {
			
			HttpServer restServer = createHttpServer();
			restServer.start();
			
			log.info("Bestia RESTful Server API started.");
			log.info("WADL available at: {}application.wadl", getURI());
		} catch(IOException ex) {
			log.warn("Could not start RESTful Server: ", ex);
			return false;
		}
		return true;
	}

	private HttpServer createHttpServer() throws IOException {
		ResourceConfig resourceConfig = new PackagesResourceConfig("net.bestia.loginserver.rest");
		// This tutorial required and then enable below line: http://crunfy.me/1DZIui5
		// crunchifyResourceConfig.getContainerResponseFilters().add(CrunchifyCORSFilter.class);
		return HttpServerFactory.create(getURI(), resourceConfig);
	}
	
	private URI getURI() {
        return UriBuilder.fromUri("http://" + HOSTNAME + "/").port(PORT).build();
    }

	public void stop() {

	}

}
