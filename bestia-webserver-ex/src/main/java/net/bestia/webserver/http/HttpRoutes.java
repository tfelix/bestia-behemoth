package net.bestia.webserver.http;

import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;

public class HttpRoutes extends AllDirectives {

	// Das hier irgendwie umsetzen: https://doc.akka.io/docs/akka-http/current/java/http/introduction.html
	
	public Route createRoute() {
		return route(
				path("hello", () -> get(() -> complete("<h1>Say hello to akka-http</h1>"))));
	}
}
