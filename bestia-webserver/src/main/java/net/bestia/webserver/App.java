package net.bestia.webserver;

import net.bestia.webserver.bestia.BestiaBehemoth;

import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;

public class App {

	// TODO Config nicht hard coden sondern über die Start Argumente erhalten.
	public static void main(String[] args) {

		// Starting up the spring framework.
		// ApplicationContext ctx = new
		// ClassPathXmlApplicationContext("spring-config.xml");

		// Create the necessary factories.
		// Currently mock some stuff.
		// ServiceFactory servFac = new HibernateServiceFactory();

		/*
		 * AccountService accService = mock(AccountService.class);
		 * 
		 * AccountServiceFactory accountServiceFactory =
		 * mock(AccountServiceFactory.class);
		 * when(accountServiceFactory.getAccount
		 * (anyInt())).thenReturn(accService);
		 * 
		 * ServiceFactory servFac = mock(ServiceFactory.class);
		 * when(servFac.getAccountServiceFactory()).thenReturn(
		 * accountServiceFactory);
		 * 
		 * // Get the bestia config. String configFile =
		 * App.class.getClassLoader()
		 * .getResource("bestia.properties").toString();
		 * 
		 * BestiaZoneserver bestiaServer = new BestiaZoneserver( servFac,
		 * BestiaWebsocketConnector.getInstance(), configFile);
		 */

		Config.Builder b = new Config.Builder();

		b.resource(BestiaBehemoth.class).resource("../bestia-www-client/source").host("0.0.0.0")
				.port(8080);

		Nettosphere server = new Nettosphere.Builder().config(b.build())
				.build();
		server.start();

		/*
		 * Nettosphere server = new Nettosphere.Builder().config( new
		 * Config.Builder() .host("127.0.0.1") .port(8080) .resource(new
		 * Handler() { public void handle(AtmosphereResource r) { try {
		 * r.getResponse
		 * ().write("Hello World").write(" from Nettosphere").flushBuffer(); }
		 * catch (IOException e) { e.printStackTrace(); } } }) .build())
		 * .build(); server.start();
		 */

		/*
		 * try { // Start the bestia server. bestiaServer.start();
		 * 
		 * server.start(); server.join(); } catch (Throwable t) {
		 * t.printStackTrace(System.err); }
		 */

	}

}
