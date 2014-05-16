package net.bestia.webserver.jetty;

import net.bestia.webserver.bestia.BestiaSocket;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

/**
 * The servlet adds the bestia websocket configuration to Jetty.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@SuppressWarnings("serial")
public class BestiaServlet extends WebSocketServlet {

	@Override
	public void configure(WebSocketServletFactory factory) {
		factory.getPolicy().setIdleTimeout(100000);
		//factory.getPolicy().s
		factory.register(BestiaSocket.class);
	}

}
