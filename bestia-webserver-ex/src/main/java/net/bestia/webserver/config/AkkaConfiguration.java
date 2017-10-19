package net.bestia.webserver.config;

import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import net.bestia.webserver.http.HttpRoutes;
import net.bestia.webserver.service.ConfigurationService;

/**
 * Generates the akka configuration file which is used to connect to the remote
 * actor system of the bestia zone server. It overwrites the default akka config
 * file "application.conf" since this is already used by spring with
 * "akka.config".
 * 
 * @author Thomas Felix
 *
 */
@Configuration
public class AkkaConfiguration {

	private final static Logger LOG = LoggerFactory.getLogger(AkkaConfiguration.class);
	private final static String AKKA_CONFIG_NAME = "akka";

	@Bean
	public ActorSystem actorSystem(Config akkaConfig,
			ConfigurationService serverConfig) {

		LOG.debug("Starting actor system.");
		final ActorSystem system = ActorSystem.create("webserver", akkaConfig);

		LOG.debug("Starting webserver root actor.");
		final Http http = Http.get(system);
		final ActorMaterializer materializer = ActorMaterializer.create(system);

		// In order to access all directives we need an instance where the
		// routes are define.
		HttpRoutes app = new HttpRoutes();

		final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = app.createRoute().flow(system, materializer);
		final CompletionStage<ServerBinding> binding = http.bindAndHandle(routeFlow,
				ConnectHttp.toHost("localhost", 8080), materializer);
		
		System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");

		return system;
	}

	@Bean
	public Config config() {
		final Config config = ConfigFactory.load(AKKA_CONFIG_NAME);
		return config;
	}
}
