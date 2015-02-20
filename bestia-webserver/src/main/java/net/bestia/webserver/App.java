package net.bestia.webserver;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import net.bestia.core.BestiaZoneserver;
import net.bestia.core.game.service.AccountService;
import net.bestia.core.game.service.AccountServiceFactory;
import net.bestia.core.game.service.HibernateServiceFactory;
import net.bestia.core.game.service.ServiceFactory;
import net.bestia.webserver.bestia.BestiaWebsocketConnector;
import net.bestia.webserver.jetty.BestiaServlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class App {

	// TODO Config nicht hard coden sondern über die Start Argumente erhalten.
	public static void main(String[] args) {
		
		// Starting up the spring framework.
		ApplicationContext ctx = new ClassPathXmlApplicationContext("spring-config.xml");

		Server server = new Server();
		ServerConnector connector = new ServerConnector(server);
		connector.setPort(8080);
		server.addConnector(connector);
		
		// Create the necessary factories.
		// Currently mock some stuff.
		//ServiceFactory servFac = new HibernateServiceFactory();
		
		AccountService accService = mock(AccountService.class);

		AccountServiceFactory accountServiceFactory = mock(AccountServiceFactory.class);
		when(accountServiceFactory.getAccount(anyInt())).thenReturn(accService);

		ServiceFactory servFac = mock(ServiceFactory.class);
		when(servFac.getAccountServiceFactory()).thenReturn(
				accountServiceFactory);
		
		// Get the bestia config.
		String configFile = App.class.getClassLoader()
                .getResource("bestia.properties").toString();
		
		BestiaZoneserver bestiaServer = new BestiaZoneserver(
				servFac, 
				BestiaWebsocketConnector.getInstance(), 
				configFile);

		// Setup the basic application "context" for this application at "/"
		// This is also known as the handler tree (in jetty speak)
		ServletContextHandler context = new ServletContextHandler(
				ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		server.setHandler(context);

		// Add a websocket to a specific path spec
		ServletHolder holderEvents = new ServletHolder("ws-events",
				BestiaServlet.class);
		context.addServlet(holderEvents, "/api");
		
		// Add the bestia gameserver to the websocket connection.
		BestiaWebsocketConnector.getInstance().setBestiaServer(bestiaServer);

		try {
			// Start the bestia server.
			bestiaServer.start();
			
			server.start();
			server.join();
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		}

	}

}
